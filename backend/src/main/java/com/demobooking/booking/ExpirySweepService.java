package com.demobooking.booking;

import com.demobooking.branch.TimeSlot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Releases holds whose confirmation link lapsed unused, and tells the customer. Runs far more
 * often than the (short, for easy demoing) confirmation TTL so expiry is fast to observe, not
 * because anything needs second-level precision.
 *
 * Also releases the held new slot for a reschedule request that lapsed unconfirmed - see
 * RescheduleService.requestReschedule. Unlike a lapsed booking hold, the appointment itself is
 * never touched by this: it was CONFIRMED before the reschedule request and stays exactly there.
 *
 * Each item gets its own transaction (via TransactionTemplate, same reasoning as
 * OptimisticRetry) rather than one transaction for the whole batch. A customer confirming a
 * booking concurrently with this sweep makes it lose on Appointment's @Version; one confirming a
 * reschedule makes it lose on Notification's @Version. Either way it throws
 * OptimisticLockingFailureException - expected, and handled by skipping just that one item, not
 * by rolling back every other legitimately-expired item swept in the same run.
 */
@Service
class ExpirySweepService {

	private static final Logger log = LoggerFactory.getLogger(ExpirySweepService.class);

	private final NotificationRepository notificationRepository;
	private final NotificationSender notificationSender;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	ExpirySweepService(
			NotificationRepository notificationRepository, NotificationSender notificationSender, PlatformTransactionManager transactionManager, Clock clock) {
		this.notificationRepository = notificationRepository;
		this.notificationSender = notificationSender;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Scheduled(fixedRateString = "${app.sweep.interval:PT15S}")
	void sweep() {
		Instant now = clock.instant();

		// Read outside any single item's transaction, ids only - each id is re-fetched fresh
		// inside its own transaction below, so a stale reference from this list is never what
		// gets mutated.
		List<UUID> expiredHoldIds = notificationRepository.findExpiredUnconsumedConfirmationRequestIds(now);
		for (UUID id : expiredHoldIds) {
			sweepOneExpiredHold(id);
		}

		List<UUID> expiredRescheduleRequestIds = notificationRepository.findExpiredUnconsumedRescheduleRequestIds(now);
		for (UUID id : expiredRescheduleRequestIds) {
			sweepOneExpiredRescheduleRequest(id);
		}
	}

	// Package-private so tests can drive one item's transaction directly. Each item method
	// swallows its own failures: one poisoned row must never abort the rest of the batch, or the
	// scheduled run itself.
	void sweepOneExpiredHold(UUID notificationId) {
		try {
			transactionTemplate.executeWithoutResult(status -> {
				Notification confirmationRequest = notificationRepository.getReferenceById(notificationId);
				Appointment appointment = confirmationRequest.getAppointment();
				// Re-checked on this transaction's fresh read: the list query may have seen the
				// hold before a confirm committed, and a confirmed appointment must keep its slot.
				if (appointment.getStatus() != AppointmentStatus.PENDING_CONFIRMATION || confirmationRequest.getTokenConsumedAt() != null) {
					return;
				}
				appointment.markExpired();

				TimeSlot slot = appointment.getTimeSlot();
				slot.release();

				SentEmail notice = notificationSender.sendExpiryNotice(appointment);
				notificationRepository.save(Notification.informational(appointment, NotificationType.EXPIRY_NOTICE, notice.body(), notice.sentAt()));
				log.info("Appointment {} expired unconfirmed; slot released", appointment.getReferenceCode());
			});
		} catch (OptimisticLockingFailureException lostRace) {
			log.info("Skipped sweeping notification {} - confirmed concurrently with this sweep", notificationId);
		} catch (RuntimeException failure) {
			log.error("Failed to sweep expired hold for notification {} - skipped, will retry next run", notificationId, failure);
		}
	}

	void sweepOneExpiredRescheduleRequest(UUID notificationId) {
		try {
			transactionTemplate.executeWithoutResult(status -> {
				Notification rescheduleRequest = notificationRepository.getReferenceById(notificationId);
				Instant now = clock.instant();
				// Re-checked here, not trusted from the list query: that read is outside this
				// transaction, and a request already consumed (confirmed, or swept by an earlier
				// run) must never release its slot again - by now that capacity may belong to
				// someone else's booking.
				if (rescheduleRequest.getTokenConsumedAt() != null || !rescheduleRequest.getTokenExpiresAt().isBefore(now)) {
					return;
				}
				Appointment appointment = rescheduleRequest.getAppointment();
				TimeSlot newSlot = rescheduleRequest.getNewTimeSlot();
				// Consuming the token is what makes this one-shot: the list query skips consumed
				// requests, so the next run cannot find it again.
				rescheduleRequest.markTokenConsumed(now);
				newSlot.release();

				SentEmail notice = notificationSender.sendRescheduleExpiryNotice(appointment, newSlot);
				notificationRepository.save(Notification.informational(appointment, NotificationType.EXPIRY_NOTICE, notice.body(), notice.sentAt()));
				log.info(
						"Reschedule request for appointment {} expired unconfirmed; held slot released, appointment unchanged",
						appointment.getReferenceCode());
			});
		} catch (OptimisticLockingFailureException lostRace) {
			log.info("Skipped sweeping reschedule request {} - confirmed concurrently with this sweep", notificationId);
		} catch (RuntimeException failure) {
			log.error("Failed to sweep expired reschedule request for notification {} - skipped, will retry next run", notificationId, failure);
		}
	}

}
