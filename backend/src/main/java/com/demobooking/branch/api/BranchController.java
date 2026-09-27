package com.demobooking.branch.api;

import com.demobooking.branch.BranchService;
import com.demobooking.branch.dto.AvailabilitySlotResponse;
import com.demobooking.branch.dto.BranchResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// See docs/api/openapi.yaml for the contract this implements.
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

	private final BranchService branchService;

	BranchController(BranchService branchService) {
		this.branchService = branchService;
	}

	@GetMapping
	public List<BranchResponse> listBranches() {
		return branchService.listActiveBranches();
	}

	@GetMapping("/{id}/availability")
	public List<AvailabilitySlotResponse> getAvailability(
			@PathVariable UUID id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam UUID serviceTypeId) {
		return branchService.getAvailability(id, serviceTypeId, date);
	}

}
