package com.demobooking.booking;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

	boolean existsByReferenceCode(String referenceCode);

	// Both reads below feed an AppointmentResponse or an authorization check, which need the slot
	// (with its branch and service type) and the customer - one query instead of four.
	@EntityGraph(attributePaths = {"timeSlot.branch", "timeSlot.serviceType", "customer"})
	Optional<Appointment> findByReferenceCodeAndCustomer_EmailIgnoreCase(String referenceCode, String email);

	@EntityGraph(attributePaths = {"timeSlot.branch", "timeSlot.serviceType", "customer"})
	Optional<Appointment> findWithDetailsById(UUID id);

}
