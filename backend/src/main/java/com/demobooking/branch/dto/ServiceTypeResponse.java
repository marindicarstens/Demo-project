package com.demobooking.branch.dto;

import com.demobooking.branch.ApplicableClientType;
import com.demobooking.branch.ServiceType;
import java.util.UUID;

// Matches the ServiceType schema in docs/api/openapi.yaml.
public record ServiceTypeResponse(UUID id, String name, int durationMinutes, ApplicableClientType applicableClientType) {

	public static ServiceTypeResponse from(ServiceType serviceType) {
		return new ServiceTypeResponse(
				serviceType.getId(), serviceType.getName(), serviceType.getDurationMinutes(), serviceType.getApplicableClientType());
	}

}
