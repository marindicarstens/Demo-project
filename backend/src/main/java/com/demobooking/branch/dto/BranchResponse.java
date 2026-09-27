package com.demobooking.branch.dto;

import com.demobooking.branch.Branch;
import java.time.LocalTime;
import java.util.UUID;

// Matches the Branch schema in docs/api/openapi.yaml.
public record BranchResponse(UUID id, String name, String address, String city, String phone, LocalTime opensAt, LocalTime closesAt) {

	public static BranchResponse from(Branch branch) {
		return new BranchResponse(
				branch.getId(), branch.getName(), branch.getAddress(), branch.getCity(), branch.getPhone(), branch.getOpensAt(), branch.getClosesAt());
	}

}
