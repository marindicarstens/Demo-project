package com.demobooking.branch;

import com.demobooking.customer.ClientType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "service_type")
public class ServiceType {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "duration_minutes", nullable = false)
	private int durationMinutes;

	@Enumerated(EnumType.STRING)
	@Column(name = "applicable_client_type", nullable = false)
	private ApplicableClientType applicableClientType;

	protected ServiceType() {
		// JPA
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public ApplicableClientType getApplicableClientType() {
		return applicableClientType;
	}

	/** Whether this service type's catalog entry is offered to the given entry flow. */
	public boolean appliesTo(ClientType clientType) {
		return applicableClientType.covers(clientType);
	}

}
