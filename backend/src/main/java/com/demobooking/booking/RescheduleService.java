package com.demobooking.booking;

import com.demobooking.booking.dto.AppointmentResponse;
import com.demobooking.booking.dto.RescheduleRequestResponse;
import com.demobooking.booking.dto.SimulatedEmailResponse;
import com.demobooking.branch.TimeSlot;
import com.demobooking.branch.TimeSlotRepository;
import com.demobooking.common.ConflictException;
import com.demobooking.common.GoneException;
import com.demobooking.common.NotFoundException;
import com.demobooking.common.OptimisticRetry;
import com.demobooking.common.SecureTokens;
import com.demobooking.common.UnprocessableEntityException;
import com.demobooking.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moving a confirmed appointment (docs/USER-GUIDE.md §5) is a request/confirm pair like booking:
 * the request holds the new slot, only the emailed link's click moves the appointment, and if the
 * link lapses {@link ExpirySweepService} releases the held slot and the appointment stays put.
 */
@Service
public class RescheduleService {

	private final TimeSlotRepository timeSlotRepository;
	private final AppointmentRepository appointmentRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationSender notificationSender;
	private final AppointmentAuthorizer authorizer;
	private final TransactionTemplate transactionTemplate;
	private final Duration minNotice;
	private final Duration confirmationTokenTtl;
	private final Clock clock;

	RescheduleService(
			TimeSlotRepository timeSlotRepository,
			AppointmentRepository appointmentRepository,
			NotificationRepository notificationRepository,
			NotificationSender notificationSender,
			AppointmentAuthorizer authorizer,
			PlatformTransactionManager transactionManager,
			AppProperties appProperties,
			Clock clock) {
		this.timeSlotRepository = timeSlotRepository;
		this.appointmentRepository = appointmentRepository;
		this.notificationRepository = notificationRepository;
		this.notificationSender = notificationSender;
		this.authorizer = authorizer;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.minNotice = appProperties.booking().rescheduleMinNotice();
		this.confirmationTokenTtl = appProperties.booking().confirmationTokenTtl();
		this.clock = clock;
	}

	/** Phase 1: holds the new slot (retrying a lost race via {@link OptimisticRetry}) and emails the confirm link. */
	public RescheduleRequestResponse requestReschedule(UUID appointmentId, UUID newSlotId, String bearerToken, String reference, String email) {
		return SlotReservation.reserveOrConflict(() -> OptimisticRetry.execute(
				transactionTemplate, () -> requestInNewTransaction(appointmentId, newSlotId, bearerToken, reference, email), OptimisticRetry.DEFAULT_MAX_ATTEMPTS));
	}

	private RescheduleRequestResponse requestInNewTransaction(UUID appointmentId, UUID newSlotId, String bearerToken, String reference, String email) {
		Appointment appointment = authorizer.loadAuthorized(appointmentId, bearerToken, reference, email);
		appointment.requireModifiable();
		Instant now = clock.instant();
		requireCurrentSlotMovable(appointment, now);
		// Only one pending request per appointment: two could each hold a slot, but only one can be applied.
		if (notificationRepository.findPendingRescheduleRequest(appointmentId, now).isPresent()) {
			throw new ConflictException("A reschedule is already pending confirmation for this appointment - check your email, or wait for that request to expire");
		}
		TimeSlot newSlot = timeSlotRepository.findById(newSlotId).orElseThrow(() -> new NotFoundException("No time slot found with id " + newSlotId));
		requireValidTarget(appointment, newSlot, now);

		newSlot.reserve(); // throws SlotFullException if full - held until the confirm click or the expiry sweep
		timeSlotRepository.saveAndFlush(newSlot); // flush now, so a lost race throws here, in this attempt
		String rawToken = SecureTokens.generateToken();
		Instant tokenExpiresAt = now.plus(confirmationTokenTtl);
		SentEmail sentEmail = notificationSender.sendRescheduleRequest(appointment, newSlot, rawToken);
		notificationRepository.save(Notification.rescheduleRequest(appointment, newSlot, sentEmail.body(), SecureTokens.hash(rawToken), tokenExpiresAt, sentEmail.sentAt()));
		return new RescheduleRequestResponse(appointment.getId(), appointment.getReferenceCode(), appointment.getStatus(), tokenExpiresAt,
				SimulatedEmailResponse.from(sentEmail));
	}

	/** Phase 2: the emailed link's explicit click (never page load - anti-prefetch). Re-validates, as time has
	 * passed; a failed check keeps the hold and the token, since the checks only get stricter and the sweep
	 * reclaims the slot once the link expires. */
	@Transactional
	public AppointmentResponse confirmReschedule(String rawToken) {
		Notification requestNotification = notificationRepository
				.findRescheduleRequestByTokenHash(SecureTokens.hash(rawToken))
				.orElseThrow(() -> new NotFoundException("Unknown reschedule confirmation token"));

		Instant now = clock.instant();
		if (!requestNotification.isTokenValid(now)) {
			throw new GoneException("This reschedule link has expired or was already used - your appointment is unchanged");
		}
		Appointment appointment = requestNotification.getAppointment();
		appointment.requireModifiable();
		TimeSlot oldSlot = appointment.getTimeSlot();
		TimeSlot newSlot = requestNotification.getNewTimeSlot();
		requireCurrentSlotMovable(appointment, now);
		requireValidTarget(appointment, newSlot, now);

		requestNotification.markTokenConsumed(now);
		oldSlot.release();
		timeSlotRepository.save(oldSlot);
		appointment.reschedule(newSlot);
		// The receipt's cancellation link expires when the appointment starts, so it follows the move.
		notificationRepository.findByAppointment_IdAndType(appointment.getId(), NotificationType.BOOKING_RECEIPT)
				.ifPresent(receipt -> receipt.setTokenExpiry(AppointmentTimes.startInstant(newSlot)));
		return AppointmentResponse.from(appointmentRepository.save(appointment));
	}

	// Minimum notice applies both ways (docs/USER-GUIDE.md §5): an imminent appointment can't be
	// moved, and it can't be moved into a slot that is itself inside the window.
	private void requireCurrentSlotMovable(Appointment appointment, Instant now) {
		if (AppointmentTimes.startInstant(appointment.getTimeSlot()).isBefore(now.plus(minNotice))) {
			throw new UnprocessableEntityException("Too close to your current appointment time to reschedule - please contact the branch directly");
		}
	}

	private void requireValidTarget(Appointment appointment, TimeSlot newSlot, Instant now) {
		if (AppointmentTimes.startInstant(newSlot).isBefore(now.plus(minNotice))) {
			throw new UnprocessableEntityException("That time is too soon - please choose a time further out");
		}
		if (!newSlot.getServiceType().appliesTo(appointment.getCustomer().getClientType())) {
			throw new UnprocessableEntityException("This service type is not available for the original entry flow");
		}
	}

}
