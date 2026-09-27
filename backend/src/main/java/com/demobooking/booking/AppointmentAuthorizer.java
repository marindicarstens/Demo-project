package com.demobooking.booking;

import com.demobooking.common.NotFoundException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Website reschedule, cancel and read accept either credential named in docs/api/openapi.yaml's
 * referenceEmailAuth/appointmentAccessToken security schemes: a valid bearer token for this
 * appointment, or its reference code plus the booking email. This is a business-rule check on top
 * of SecurityConfig's permit-all, not Spring Security authentication.
 */
@Component
class AppointmentAuthorizer {

	private final AppointmentRepository appointmentRepository;
	private final AppointmentAccessTokenService accessTokenService;

	AppointmentAuthorizer(AppointmentRepository appointmentRepository, AppointmentAccessTokenService accessTokenService) {
		this.appointmentRepository = appointmentRepository;
		this.accessTokenService = accessTokenService;
	}

	/** A mismatched credential and a nonexistent appointment id throw the same 404 on purpose: a
	 * 401/403 would confirm to an unauthorized caller that the appointment exists. */
	Appointment loadAuthorized(UUID appointmentId, String bearerToken, String reference, String email) {
		Appointment appointment = appointmentRepository.findWithDetailsById(appointmentId).orElseThrow(AppointmentAuthorizer::notFound);

		boolean tokenAuthorized = Optional.ofNullable(bearerToken)
				.flatMap(accessTokenService::verify)
				.map(appointmentId::equals)
				.orElse(false);
		boolean referenceEmailAuthorized = reference != null
				&& email != null
				&& appointment.getReferenceCode().equalsIgnoreCase(normaliseReference(reference))
				&& appointment.getCustomer().getEmail().equalsIgnoreCase(email.trim());

		if (!tokenAuthorized && !referenceEmailAuthorized) {
			throw notFound();
		}
		return appointment;
	}

	/** Reference codes are stored upper-case, so every comparison forgives case and stray whitespace. */
	static String normaliseReference(String reference) {
		return reference.trim().toUpperCase(Locale.ROOT);
	}

	private static NotFoundException notFound() {
		return new NotFoundException("No appointment found for that id");
	}

}
