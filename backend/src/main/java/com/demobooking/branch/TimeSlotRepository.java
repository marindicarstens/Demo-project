package com.demobooking.branch;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

	boolean existsByBranchAndServiceTypeAndSlotDate(Branch branch, ServiceType serviceType, LocalDate slotDate);

	@Query("""
			SELECT t FROM TimeSlot t
			WHERE t.branch.id = :branchId
			  AND t.serviceType.id = :serviceTypeId
			  AND t.slotDate = :slotDate
			  AND t.bookedCount < t.capacity
			ORDER BY t.startTime ASC
			""")
	List<TimeSlot> findAvailable(
			@Param("branchId") UUID branchId, @Param("serviceTypeId") UUID serviceTypeId, @Param("slotDate") LocalDate slotDate);

}
