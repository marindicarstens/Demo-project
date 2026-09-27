package com.demobooking.booking.dto;

import com.demobooking.booking.AppointmentStatus;
import java.time.Instant;
import java.util.UUID;

// Matches the RescheduleRequestResult schema in docs/api/openapi.yaml.
public record RescheduleRequestResponse(
		UUID appointmentId, String referenceCode, AppointmentStatus status, Instant rescheduleRequestExpiresAt, SimulatedEmailResponse simulatedEmail) {
}
