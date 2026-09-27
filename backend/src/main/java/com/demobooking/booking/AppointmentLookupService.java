package com.demobooking.booking;

import com.demobooking.booking.dto.AppointmentResponse;
import com.demobooking.common.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to an existing appointment - see docs/USER-GUIDE.md §5. */
@Service
public class AppointmentLookupService {

	private final AppointmentRepository appointmentRepository;
	private final AppointmentAuthorizer authorizer;

	AppointmentLookupService(AppointmentRepository appointmentRepository, AppointmentAuthorizer authorizer) {
		this.appointmentRepository = appointmentRepository;
		this.authorizer = authorizer;
	}

	@Transactional(readOnly = true)
	public AppointmentResponse lookup(String reference, String email) {
		Appointment appointment = appointmentRepository
				.findByReferenceCodeAndCustomer_EmailIgnoreCase(AppointmentAuthorizer.normaliseReference(reference), email.trim())
				.orElseThrow(() -> new NotFoundException("No appointment found for that reference and email"));
		return AppointmentResponse.from(appointment);
	}

	/** Read-only view for either credential - lets a token holder re-fetch without a ref+email. */
	@Transactional(readOnly = true)
	public AppointmentResponse get(UUID appointmentId, String bearerToken, String reference, String email) {
		return AppointmentResponse.from(authorizer.loadAuthorized(appointmentId, bearerToken, reference, email));
	}

}
