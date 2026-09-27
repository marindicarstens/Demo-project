package com.demobooking.booking.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// Matches the RescheduleRequest schema in docs/api/openapi.yaml.
public record RescheduleRequest(@NotNull UUID newSlotId) {
}
