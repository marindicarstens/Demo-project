package com.demobooking.branch;

import com.demobooking.branch.dto.AvailabilitySlotResponse;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import com.demobooking.common.AppTimeZone;
import com.demobooking.common.NotFoundException;
import com.demobooking.customer.ClientType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Branch/service-type reads are cached - they change rarely.
 * Availability is deliberately NOT cached - it changes every time a slot is booked, so a stale
 * cached read here would show a slot as available after someone else just took it.
 */
@Service
public class BranchService {

	private final BranchRepository branchRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final TimeSlotRepository timeSlotRepository;
	private final Clock clock;

	BranchService(BranchRepository branchRepository, ServiceTypeRepository serviceTypeRepository, TimeSlotRepository timeSlotRepository, Clock clock) {
		this.branchRepository = branchRepository;
		this.serviceTypeRepository = serviceTypeRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.clock = clock;
	}

	@Cacheable("branches")
	public List<BranchResponse> listActiveBranches() {
		return branchRepository.findByActiveTrueOrderByNameAsc().stream().map(BranchResponse::from).toList();
	}

	@Cacheable(value = "serviceTypes", key = "#clientType")
	public List<ServiceTypeResponse> listServiceTypes(ClientType clientType) {
		return serviceTypeRepository.findAllByOrderByNameAsc().stream()
				.filter(serviceType -> serviceType.appliesTo(clientType))
				.map(ServiceTypeResponse::from)
				.toList();
	}

	public List<AvailabilitySlotResponse> getAvailability(UUID branchId, UUID serviceTypeId, LocalDate date) {
		// An inactive branch is hidden from listActiveBranches, so it must not offer slots either.
		branchRepository.findById(branchId)
				.filter(Branch::isActive)
				.orElseThrow(() -> new NotFoundException("No branch found with id " + branchId));
		ZonedDateTime now = clock.instant().atZone(AppTimeZone.ZONE);
		LocalDate today = now.toLocalDate();
		if (date.isBefore(today)) {
			return List.of();
		}
		return timeSlotRepository.findAvailable(branchId, serviceTypeId, date).stream()
				.filter(slot -> !date.isEqual(today) || slot.getStartTime().isAfter(now.toLocalTime()))
				.map(AvailabilitySlotResponse::from)
				.toList();
	}

}
