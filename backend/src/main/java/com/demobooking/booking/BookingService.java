package com.demobooking.booking;

import com.demobooking.booking.dto.AppointmentHoldResponse;
import com.demobooking.booking.dto.BookingRequest;
import com.demobooking.booking.dto.ConfirmationResultResponse;
import com.demobooking.booking.dto.ExistingClientBookingRequest;
import com.demobooking.booking.dto.NewClientBookingRequest;
import com.demobooking.booking.dto.SimulatedEmailResponse;
import com.demobooking.branch.TimeSlot;
import com.demobooking.branch.TimeSlotRepository;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import com.demobooking.common.GoneException;
import com.demobooking.common.NotFoundException;
import com.demobooking.common.OptimisticRetry;
import com.demobooking.common.SecureTokens;
import com.demobooking.common.UnprocessableEntityException;
import com.demobooking.config.AppProperties;
import com.demobooking.customer.ClientType;
import com.demobooking.customer.Customer;
import com.demobooking.customer.CustomerRepository;
import com.demobooking.customer.DirectoryValidationException;
import com.demobooking.customer.DirectoryValidationRateLimiter;
import com.demobooking.customer.DirectoryValidationService;
import com.demobooking.customer.ExistingCustomer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Booking-hold creation and confirmation. The slot reservation retries lost races through
 * {@link OptimisticRetry}, one fresh transaction per attempt.
 *
 * For the existing-client flow, directory validation (and its rate limit) runs once, before any
 * slot reservation or write: an unvalidated request must never be able to tie up real capacity,
 * so the gate always comes before the hold, not after it.
 */
@Service
public class BookingService {

	private static final Logger log = LoggerFactory.getLogger(BookingService.class);

	private final TimeSlotRepository timeSlotRepository;
	private final CustomerRepository customerRepository;
	private final AppointmentRepository appointmentRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationSender notificationSender;
	private final AppointmentAccessTokenService accessTokenService;
	private final DirectoryValidationService directoryValidationService;
	private final DirectoryValidationRateLimiter directoryValidationRateLimiter;
	private final TransactionTemplate transactionTemplate;
	private final Duration confirmationTokenTtl;
	private final Clock clock;

	BookingService(
			TimeSlotRepository timeSlotRepository,
			CustomerRepository customerRepository,
			AppointmentRepository appointmentRepository,
			NotificationRepository notificationRepository,
			NotificationSender notificationSender,
			AppointmentAccessTokenService accessTokenService,
			DirectoryValidationService directoryValidationService,
			DirectoryValidationRateLimiter directoryValidationRateLimiter,
			PlatformTransactionManager transactionManager,
			AppProperties appProperties,
			Clock clock) {
		this.timeSlotRepository = timeSlotRepository;
		this.customerRepository = customerRepository;
		this.appointmentRepository = appointmentRepository;
		this.notificationRepository = notificationRepository;
		this.notificationSender = notificationSender;
		this.accessTokenService = accessTokenService;
		this.directoryValidationService = directoryValidationService;
		this.directoryValidationRateLimiter = directoryValidationRateLimiter;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.confirmationTokenTtl = appProperties.booking().confirmationTokenTtl();
		this.clock = clock;
	}

	/**
	 * Identity resolved for the customer making this booking - either the new-client's own
	 * submitted details, or the matched {@link ExistingCustomer}'s record. Resolved once, before
	 * the slot-reservation retry loop, so a directory match (and its rate limit) is checked
	 * exactly once per booking attempt, not once per retry - since validation must happen before
	 * any write.
	 */
	private record ResolvedCustomer(String fullName, String email, String phone, ClientType clientType, UUID matchedExistingCustomerId) {
	}

	public AppointmentHoldResponse createHold(BookingRequest request, String clientIp) {
		ResolvedCustomer resolvedCustomer = resolveCustomer(request, clientIp);
		return SlotReservation.reserveOrConflict(() -> OptimisticRetry.execute(
				transactionTemplate, () -> createHoldInNewTransaction(request, resolvedCustomer), OptimisticRetry.DEFAULT_MAX_ATTEMPTS));
	}

	private ResolvedCustomer resolveCustomer(BookingRequest request, String clientIp) {
		return switch (request) {
			case NewClientBookingRequest r -> new ResolvedCustomer(r.fullName(), r.email(), r.phone(), ClientType.NEW_CLIENT, null);
			case ExistingClientBookingRequest r -> {
				directoryValidationRateLimiter.checkAndRecordAttempt(clientIp);
				ExistingCustomer matched = directoryValidationService
						.validate(r.email(), r.idNumber(), r.accountNumber())
						.orElseThrow(() -> {
							log.warn("Existing-client directory validation failed for ip={}, emailHash={}", clientIp, SecureTokens.hash(r.email()));
							return new DirectoryValidationException("No matching client record found for those details");
						});
				yield new ResolvedCustomer(matched.getFullName(), matched.getEmail(), matched.getPhone(), ClientType.EXISTING_CLIENT, matched.getId());
			}
		};
	}

	private AppointmentHoldResponse createHoldInNewTransaction(BookingRequest request, ResolvedCustomer resolvedCustomer) {
		TimeSlot slot = timeSlotRepository
				.findById(request.slotId())
				.orElseThrow(() -> new NotFoundException("No time slot found with id " + request.slotId()));
		// The slot alone decides where and what is booked, so a request naming a different branch or
		// service type is inconsistent client input, not something to silently ignore.
		if (!slot.getBranch().getId().equals(request.branchId()) || !slot.getServiceType().getId().equals(request.serviceTypeId())) {
			throw new UnprocessableEntityException("The selected time slot does not belong to that branch and service type");
		}
		// Server-side enforcement of the flow/service-type pairing - the client-chosen flow is a
		// parameter the server must not blindly trust.
		if (!slot.getServiceType().appliesTo(resolvedCustomer.clientType())) {
			throw new UnprocessableEntityException("This service type is not available for the selected entry flow");
		}
		// The availability listing (BranchService.getAvailability) already excludes today's
		// already-passed times, but that's a client-facing filter, not a guarantee - a direct
		// POST with a stale or crafted slotId for a slot that's already started must still be
		// rejected here, the same "don't trust the client" reasoning as the check above.
		Instant now = clock.instant();
		if (AppointmentTimes.startInstant(slot).isBefore(now)) {
			throw new UnprocessableEntityException("That time has already passed - please pick another");
		}
		slot.reserve(); // throws SlotFullException if full - see BookingService class Javadoc
		timeSlotRepository.saveAndFlush(slot); // flush now, so a lost race throws here, in this attempt

		Customer customer = customerRepository.save(new Customer(
				resolvedCustomer.fullName(),
				resolvedCustomer.email(),
				resolvedCustomer.phone(),
				resolvedCustomer.clientType(),
				resolvedCustomer.matchedExistingCustomerId()));
		Appointment appointment = appointmentRepository.save(new Appointment(slot, customer, generateUniqueReferenceCode(), now));

		String rawToken = SecureTokens.generateToken();
		Instant tokenExpiresAt = now.plus(confirmationTokenTtl);
		SentEmail email = notificationSender.sendConfirmationRequest(appointment, rawToken);
		notificationRepository.save(Notification.withToken(
				appointment, NotificationType.CONFIRMATION_REQUEST, email.body(), SecureTokens.hash(rawToken), tokenExpiresAt, email.sentAt()));

		return new AppointmentHoldResponse(
				appointment.getId(),
				appointment.getReferenceCode(),
				appointment.getStatus(),
				tokenExpiresAt,
				SimulatedEmailResponse.from(email));
	}

	@Transactional
	public ConfirmationResultResponse confirm(String rawToken) {
		String tokenHash = SecureTokens.hash(rawToken);
		Notification requestNotification = notificationRepository
				.findByTypeAndTokenHash(NotificationType.CONFIRMATION_REQUEST, tokenHash)
				.orElseThrow(() -> new NotFoundException("Unknown confirmation token"));

		Instant now = clock.instant();
		if (!requestNotification.isTokenValid(now)) {
			throw new GoneException("This confirmation link has expired or was already used - please book again");
		}
		requestNotification.markTokenConsumed(now);

		Appointment appointment = requestNotification.getAppointment();
		appointment.confirm();

		// Valid until the appointment itself starts - cancelling a past
		// appointment is meaningless, so that's the natural expiry.
		String rawCancellationToken = SecureTokens.generateToken();
		Instant cancellationTokenExpiresAt = AppointmentTimes.startInstant(appointment.getTimeSlot());
		SentEmail receipt = notificationSender.sendBookingReceipt(appointment, rawCancellationToken);
		notificationRepository.save(Notification.withToken(
				appointment, NotificationType.BOOKING_RECEIPT, receipt.body(), SecureTokens.hash(rawCancellationToken), cancellationTokenExpiresAt, receipt.sentAt()));

		AppointmentAccessTokenService.IssuedToken issued = accessTokenService.issueFor(appointment.getId());

		TimeSlot slot = appointment.getTimeSlot();
		return new ConfirmationResultResponse(
				appointment.getId(),
				appointment.getReferenceCode(),
				appointment.getStatus(),
				issued.token(),
				issued.expiresAt(),
				SimulatedEmailResponse.from(receipt),
				BranchResponse.from(slot.getBranch()),
				ServiceTypeResponse.from(slot.getServiceType()),
				slot.getSlotDate(),
				slot.getStartTime());
	}

	private String generateUniqueReferenceCode() {
		String code;
		do {
			code = SecureTokens.generateReferenceCode();
		} while (appointmentRepository.existsByReferenceCode(code));
		return code;
	}

}
