package com.demobooking.booking.dto;

import com.demobooking.booking.AppointmentStatus;
import java.time.Instant;
import java.util.UUID;

// Matches the AppointmentHold schema in docs/api/openapi.yaml.
public record AppointmentHoldResponse(
		UUID appointmentId, String referenceCode, AppointmentStatus status, Instant holdExpiresAt, SimulatedEmailResponse simulatedEmail) {
}
