package com.demobooking.booking.dto;

import com.demobooking.booking.AppointmentStatus;

// Matches the CancellationResult schema in docs/api/openapi.yaml.
public record CancellationResultResponse(String referenceCode, AppointmentStatus status) {
}
