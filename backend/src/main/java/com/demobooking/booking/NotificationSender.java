package com.demobooking.booking;

import com.demobooking.branch.TimeSlot;

/**
 * The port booking sends its emails through: an adapter (today only the simulated one) renders
 * and delivers each email and returns what it said. Booking owns the port and persists the
 * token-bearing {@link Notification} rows itself, so replacing the adapter never touches the
 * tokens that confirm, cancel, reschedule and the expiry sweep rely on.
 */
public interface NotificationSender {

	/** The magic-link "please confirm" email, sent at hold creation. */
	SentEmail sendConfirmationRequest(Appointment appointment, String rawToken);

	/** The receipt email sent on confirmation, carrying the cancellation link. */
	SentEmail sendBookingReceipt(Appointment appointment, String rawCancellationToken);

	/** Sent by the expiry sweep when a hold lapses unconfirmed. */
	SentEmail sendExpiryNotice(Appointment appointment);

	/** The magic-link "please confirm this reschedule" email for an already-CONFIRMED appointment. */
	SentEmail sendRescheduleRequest(Appointment appointment, TimeSlot newSlot, String rawToken);

	/** Sent by the expiry sweep when a reschedule request lapses; the appointment itself is untouched. */
	SentEmail sendRescheduleExpiryNotice(Appointment appointment, TimeSlot newSlot);

}
