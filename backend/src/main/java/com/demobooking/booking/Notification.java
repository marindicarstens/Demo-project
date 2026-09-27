package com.demobooking.booking;

import com.demobooking.branch.TimeSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A simulated notification - never actually sent. token_hash / token_expires_at /
 * token_consumed_at are reused across types with type-appropriate meaning
 * (the confirm token for CONFIRMATION_REQUEST, the cancellation token for BOOKING_RECEIPT, the
 * reschedule-confirm token for RESCHEDULE_REQUEST, null for EXPIRY_NOTICE) rather than
 * proliferating type-specific columns. new_time_slot_id follows the same idea but is only ever
 * set for RESCHEDULE_REQUEST - it's the one type that needs to remember a slot other than the
 * appointment's current one (the slot being requested, not yet applied).
 *
 * version exists for RESCHEDULE_REQUEST: confirmReschedule and the reschedule-expiry sweep both
 * consume that token, and it's the only row both write. Without it both could commit - the
 * appointment moved onto the new slot and that slot's capacity released anyway.
 */
@Entity
@Table(name = "notification")
public class Notification {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "appointment_id")
	private Appointment appointment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationChannel channel;

	// No @Lob: on Postgres that maps a String to an oid large-object reference, not a plain
	// text column - "TEXT" in Postgres already holds arbitrary-length strings without it.
	@Column(name = "simulated_payload", nullable = false)
	private String simulatedPayload;

	@Column(name = "token_hash")
	private String tokenHash;

	@Column(name = "token_expires_at")
	private Instant tokenExpiresAt;

	@Column(name = "token_consumed_at")
	private Instant tokenConsumedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "new_time_slot_id")
	private TimeSlot newTimeSlot;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	@Version
	private long version;

	protected Notification() {
		// JPA
	}

	// The fields every type shares; the factories below add what their type carries.
	private Notification(Appointment appointment, NotificationType type, String simulatedPayload, Instant sentAt) {
		this.appointment = appointment;
		this.type = type;
		this.channel = NotificationChannel.EMAIL_SIMULATED;
		this.simulatedPayload = simulatedPayload;
		this.sentAt = sentAt;
	}

	/** For CONFIRMATION_REQUEST and BOOKING_RECEIPT, which carry an action token. */
	public static Notification withToken(
			Appointment appointment, NotificationType type, String simulatedPayload, String tokenHash, Instant tokenExpiresAt, Instant sentAt) {
		Notification notification = new Notification(appointment, type, simulatedPayload, sentAt);
		notification.tokenHash = tokenHash;
		notification.tokenExpiresAt = tokenExpiresAt;
		return notification;
	}

	/** For RESCHEDULE_REQUEST, which carries both an action token and the requested new slot -
	 * see this class's Javadoc. */
	public static Notification rescheduleRequest(
			Appointment appointment, TimeSlot newTimeSlot, String simulatedPayload, String tokenHash, Instant tokenExpiresAt, Instant sentAt) {
		Notification notification = withToken(appointment, NotificationType.RESCHEDULE_REQUEST, simulatedPayload, tokenHash, tokenExpiresAt, sentAt);
		notification.newTimeSlot = newTimeSlot;
		return notification;
	}

	/** For EXPIRY_NOTICE, which is informational only - no token. */
	public static Notification informational(Appointment appointment, NotificationType type, String simulatedPayload, Instant sentAt) {
		return new Notification(appointment, type, simulatedPayload, sentAt);
	}

	public boolean isTokenValid(Instant now) {
		return tokenConsumedAt == null && tokenExpiresAt != null && tokenExpiresAt.isAfter(now);
	}

	public void markTokenConsumed(Instant now) {
		this.tokenConsumedAt = now;
	}

	/** Moves the token's expiry either way - a receipt's cancellation link lives until the
	 * appointment starts, so a reschedule to an earlier slot shortens it. */
	public void setTokenExpiry(Instant tokenExpiresAt) {
		this.tokenExpiresAt = tokenExpiresAt;
	}

	public UUID getId() {
		return id;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public Appointment getAppointment() {
		return appointment;
	}

	public NotificationType getType() {
		return type;
	}

	public String getSimulatedPayload() {
		return simulatedPayload;
	}

	public Instant getTokenExpiresAt() {
		return tokenExpiresAt;
	}

	public Instant getTokenConsumedAt() {
		return tokenConsumedAt;
	}

	public TimeSlot getNewTimeSlot() {
		return newTimeSlot;
	}

}
