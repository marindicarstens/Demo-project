package com.demobooking.booking;

public enum NotificationType {
	/** The magic-link "please confirm your appointment" email, sent at hold creation. */
	CONFIRMATION_REQUEST,
	/** Sent on confirm; carries the cancellation link/token. */
	BOOKING_RECEIPT,
	/** Sent by the expiry sweep if a hold lapses unconfirmed, or a reschedule request lapses. */
	EXPIRY_NOTICE,
	/** The magic-link "please confirm this reschedule" email for a CONFIRMED appointment. Carries
	 * the requested new_time_slot_id, because the move only happens when the link is clicked. */
	RESCHEDULE_REQUEST
}
