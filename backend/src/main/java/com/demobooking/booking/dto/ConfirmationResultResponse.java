package com.demobooking.booking.dto;

import com.demobooking.booking.AppointmentStatus;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

// Matches the ConfirmationResult schema in docs/api/openapi.yaml. branch/serviceType/date/
// startTime are included so the post-confirmation screen can offer reschedule/cancel
// (docs/USER-GUIDE.md §5-6, "stays unlocked for that browsing session") without a second round
// trip.
public record ConfirmationResultResponse(
		UUID appointmentId,
		String referenceCode,
		AppointmentStatus status,
		String accessToken,
		Instant accessTokenExpiresAt,
		SimulatedEmailResponse receiptEmail,
		BranchResponse branch,
		ServiceTypeResponse serviceType,
		LocalDate date,
		LocalTime startTime) {
}
