package com.demobooking.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExistingCustomerRepository extends JpaRepository<ExistingCustomer, UUID> {

	// IgnoreCase to match every other email lookup in the app (e.g.
	// AppointmentRepository.findByReferenceCodeAndCustomer_EmailIgnoreCase) - a genuine existing
	// client typing their own email with different casing than it was seeded with must still match.
	Optional<ExistingCustomer> findByEmailIgnoreCase(String email);

}
