package com.demobooking.booking.dto;

import com.demobooking.customer.ClientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** Matches the NewClientBookingRequest schema in docs/api/openapi.yaml. */
public record NewClientBookingRequest(
		@NotNull ClientType clientType,
		@NotNull UUID branchId,
		@NotNull UUID serviceTypeId,
		@NotNull UUID slotId,
		@NotBlank String fullName,
		@NotBlank @Email String email,
		// SA local (0821234567) or international (+27821234567) - the frontend strips spaces before
		// sending, so this doesn't need to tolerate them.
		@NotBlank @Pattern(regexp = "^(\\+27|0)[1-9]\\d{8}$") String phone) implements BookingRequest {
}
