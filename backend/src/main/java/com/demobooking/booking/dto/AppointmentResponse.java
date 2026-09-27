package com.demobooking.booking.dto;

import com.demobooking.booking.Appointment;
import com.demobooking.booking.AppointmentStatus;
import com.demobooking.branch.TimeSlot;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

// Matches the Appointment schema in docs/api/openapi.yaml.
public record AppointmentResponse(
		UUID id,
		String referenceCode,
		AppointmentStatus status,
		BranchResponse branch,
		ServiceTypeResponse serviceType,
		LocalDate date,
		LocalTime startTime,
		Instant createdAt) {

	public static AppointmentResponse from(Appointment appointment) {
		TimeSlot slot = appointment.getTimeSlot();
		return new AppointmentResponse(
				appointment.getId(),
				appointment.getReferenceCode(),
				appointment.getStatus(),
				BranchResponse.from(slot.getBranch()),
				ServiceTypeResponse.from(slot.getServiceType()),
				slot.getSlotDate(),
				slot.getStartTime(),
				appointment.getCreatedAt());
	}

}
