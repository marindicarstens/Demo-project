package com.demobooking.branch.api;

import com.demobooking.branch.BranchService;
import com.demobooking.branch.dto.ServiceTypeResponse;
import com.demobooking.customer.ClientType;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-types")
public class ServiceTypeController {

	private final BranchService branchService;

	ServiceTypeController(BranchService branchService) {
		this.branchService = branchService;
	}

	@GetMapping
	public List<ServiceTypeResponse> listServiceTypes(@RequestParam ClientType clientType) {
		return branchService.listServiceTypes(clientType);
	}

}
