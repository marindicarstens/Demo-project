package com.demobooking.branch.dto;

import com.demobooking.branch.TimeSlot;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

// Matches the AvailabilitySlot schema in docs/api/openapi.yaml.
public record AvailabilitySlotResponse(UUID id, LocalDate date, LocalTime startTime, int remainingCapacity) {

	public static AvailabilitySlotResponse from(TimeSlot slot) {
		return new AvailabilitySlotResponse(slot.getId(), slot.getSlotDate(), slot.getStartTime(), slot.getRemainingCapacity());
	}

}
