package com.demobooking.booking;

import com.demobooking.booking.dto.CancellationPreviewResponse;
import com.demobooking.booking.dto.CancellationResultResponse;
import com.demobooking.branch.TimeSlot;
import com.demobooking.branch.TimeSlotRepository;
import com.demobooking.common.GoneException;
import com.demobooking.common.NotFoundException;
import com.demobooking.common.OptimisticRetry;
import com.demobooking.common.SecureTokens;
import com.demobooking.common.UnprocessableEntityException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancelling a confirmed appointment - see docs/USER-GUIDE.md §6. Two entry points: the website
 * (either credential, see {@link AppointmentAuthorizer}) and the receipt email's link, which is a
 * read-only preview plus an explicit confirm. Both are idempotent: cancelling a CANCELLED
 * appointment again succeeds without changing anything.
 */
@Service
public class CancellationService {

	private final AppointmentRepository appointmentRepository;
	private final TimeSlotRepository timeSlotRepository;
	private final NotificationRepository notificationRepository;
	private final AppointmentAuthorizer authorizer;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	CancellationService(
			AppointmentRepository appointmentRepository,
			TimeSlotRepository timeSlotRepository,
			NotificationRepository notificationRepository,
			AppointmentAuthorizer authorizer,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.notificationRepository = notificationRepository;
		this.authorizer = authorizer;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	// A lost race (e.g. with a concurrent reschedule confirm) is retried on a fresh read; if it
	// keeps losing, the conflict surfaces as a 409.
	public void cancelFromWebsite(UUID appointmentId, String bearerToken, String reference, String email) {
		OptimisticRetry.execute(transactionTemplate, () -> {
			cancelIfConfirmed(authorizer.loadAuthorized(appointmentId, bearerToken, reference, email));
			return null;
		}, OptimisticRetry.DEFAULT_MAX_ATTEMPTS);
	}

	@Transactional(readOnly = true)
	public CancellationPreviewResponse previewCancellation(String rawToken) {
		Notification receipt = lookupReceipt(rawToken);
		Appointment appointment = receipt.getAppointment();
		boolean alreadyCancelled = appointment.getStatus() == AppointmentStatus.CANCELLED;
		if (!alreadyCancelled && receipt.getTokenExpiresAt().isBefore(clock.instant())) {
			throw new GoneException("This cancellation link has expired - the appointment has already passed");
		}
		TimeSlot slot = appointment.getTimeSlot();
		return new CancellationPreviewResponse(
				appointment.getReferenceCode(), slot.getBranch().getName(), slot.getServiceType().getName(), slot.getSlotDate(), slot.getStartTime(), alreadyCancelled);
	}

	@Transactional
	public CancellationResultResponse confirmCancellation(String rawToken) {
		Notification receipt = lookupReceipt(rawToken);
		Appointment appointment = receipt.getAppointment();
		if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
			if (receipt.getTokenExpiresAt().isBefore(clock.instant())) {
				throw new GoneException("This cancellation link has expired - the appointment has already passed");
			}
			cancelIfConfirmed(appointment);
		}
		return new CancellationResultResponse(appointment.getReferenceCode(), appointment.getStatus());
	}

	/** Releases the slot and cancels the appointment - a no-op if it's already CANCELLED. */
	private void cancelIfConfirmed(Appointment appointment) {
		if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
			return;
		}
		appointment.requireModifiable();
		TimeSlot slot = appointment.getTimeSlot();
		// The only time check on the website path; on the token path the receipt-expiry 410 fires first.
		if (!AppointmentTimes.startInstant(slot).isAfter(clock.instant())) {
			throw new UnprocessableEntityException("This appointment has already started");
		}
		slot.release();
		timeSlotRepository.save(slot);
		appointment.cancel();
		// Flushed here so a lost race surfaces inside this attempt, where OptimisticRetry can retry it.
		appointmentRepository.saveAndFlush(appointment);
	}

	private Notification lookupReceipt(String rawToken) {
		return notificationRepository
				.findByTypeAndTokenHash(NotificationType.BOOKING_RECEIPT, SecureTokens.hash(rawToken))
				.orElseThrow(() -> new NotFoundException("Unknown cancellation token"));
	}

}
