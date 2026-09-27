package com.demobooking.booking.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// Matches the CancellationPreview schema in docs/api/openapi.yaml.
public record CancellationPreviewResponse(
		String referenceCode, String branchName, String serviceTypeName, LocalDate date, LocalTime startTime, boolean alreadyCancelled) {
}
