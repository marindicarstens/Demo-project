package com.demobooking.booking.dto;

import com.demobooking.customer.ClientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Matches the ExistingClientBookingRequest schema in docs/api/openapi.yaml. idNumber/accountNumber
 * are compared as plain strings against the seeded directory - see DirectoryValidationService.
 */
public record ExistingClientBookingRequest(
		@NotNull ClientType clientType,
		@NotNull UUID branchId,
		@NotNull UUID serviceTypeId,
		@NotNull UUID slotId,
		@NotBlank @Email String email,
		@NotBlank @Pattern(regexp = "\\d{13}", message = "must be a 13-digit South African ID number") String idNumber,
		@NotBlank String accountNumber) implements BookingRequest {
}
