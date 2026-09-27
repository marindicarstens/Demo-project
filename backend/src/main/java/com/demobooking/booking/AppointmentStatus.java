package com.demobooking.booking;

// Stored by name (EnumType.STRING) and sent by name in the API, so renaming a constant is a
// schema and contract change.
public enum AppointmentStatus {
	PENDING_CONFIRMATION,
	CONFIRMED,
	EXPIRED,
	CANCELLED
}
