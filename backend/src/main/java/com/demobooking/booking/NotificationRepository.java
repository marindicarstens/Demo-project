package com.demobooking.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	// Every token flow goes on to read the appointment's slot and customer, so load them in one query.
	@EntityGraph(attributePaths = {"appointment.timeSlot.branch", "appointment.timeSlot.serviceType", "appointment.customer"})
	Optional<Notification> findByTypeAndTokenHash(NotificationType type, String tokenHash);

	/** An appointment is confirmed at most once, so it has at most one BOOKING_RECEIPT. */
	Optional<Notification> findByAppointment_IdAndType(UUID appointmentId, NotificationType type);

	/** confirmReschedule also reads the requested new slot's branch and service type, so load those too. */
	@EntityGraph(attributePaths = {
			"appointment.timeSlot.branch", "appointment.timeSlot.serviceType", "appointment.customer", "newTimeSlot.branch", "newTimeSlot.serviceType"})
	Optional<Notification> findWithNewTimeSlotByTypeAndTokenHash(NotificationType type, String tokenHash);

	default Optional<Notification> findRescheduleRequestByTokenHash(String tokenHash) {
		return findWithNewTimeSlotByTypeAndTokenHash(NotificationType.RESCHEDULE_REQUEST, tokenHash);
	}

	// Ids only: the sweep re-reads each row in its own transaction, so nothing here needs fetching.
	@Query("""
			SELECT n.id FROM Notification n JOIN n.appointment a
			WHERE n.type = :type
			  AND n.tokenConsumedAt IS NULL
			  AND n.tokenExpiresAt < :now
			  AND a.status = :status
			""")
	List<UUID> findExpiredUnconsumed(@Param("type") NotificationType type, @Param("now") Instant now, @Param("status") AppointmentStatus status);

	@Query("""
			SELECT n.id FROM Notification n
			WHERE n.type = :type
			  AND n.tokenConsumedAt IS NULL
			  AND n.tokenExpiresAt < :now
			""")
	List<UUID> findExpiredUnconsumed(@Param("type") NotificationType type, @Param("now") Instant now);

	@Query("""
			SELECT n FROM Notification n
			WHERE n.type = :type
			  AND n.appointment.id = :appointmentId
			  AND n.tokenConsumedAt IS NULL
			  AND n.tokenExpiresAt >= :now
			""")
	Optional<Notification> findUnconsumedValid(@Param("type") NotificationType type, @Param("appointmentId") UUID appointmentId, @Param("now") Instant now);

	default List<UUID> findExpiredUnconsumedConfirmationRequestIds(Instant now) {
		return findExpiredUnconsumed(NotificationType.CONFIRMATION_REQUEST, now, AppointmentStatus.PENDING_CONFIRMATION);
	}

	/** No appointment-status filter: the appointment stays CONFIRMED for the whole pending-reschedule
	 * window, so the sweep must release the held new slot whatever the appointment's status. */
	default List<UUID> findExpiredUnconsumedRescheduleRequestIds(Instant now) {
		return findExpiredUnconsumed(NotificationType.RESCHEDULE_REQUEST, now);
	}

	/** Two overlapping requests could each hold a different new slot, but only one could ever be
	 * applied - see RescheduleService.requestReschedule. */
	default Optional<Notification> findPendingRescheduleRequest(UUID appointmentId, Instant now) {
		return findUnconsumedValid(NotificationType.RESCHEDULE_REQUEST, appointmentId, now);
	}

}
