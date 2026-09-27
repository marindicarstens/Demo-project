package com.demobooking.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * The contact record for one booking. Not a customer account - there is no login, so nothing
 * here is reused across bookings by the same person.
 */
@Entity
@Table(name = "customer")
public class Customer {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "full_name", nullable = false)
	private String fullName;

	@Column(nullable = false)
	private String email;

	@Column(name = "phone")
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(name = "client_type", nullable = false)
	private ClientType clientType;

	// Set on successful directory validation (existing-client flow only).
	@Column(name = "matched_existing_customer_id")
	private UUID matchedExistingCustomerId;

	protected Customer() {
		// JPA
	}

	public Customer(String fullName, String email, String phone, ClientType clientType) {
		this(fullName, email, phone, clientType, null);
	}

	public Customer(
			String fullName, String email, String phone, ClientType clientType, UUID matchedExistingCustomerId) {
		this.fullName = fullName;
		this.email = email;
		this.phone = phone;
		this.clientType = clientType;
		this.matchedExistingCustomerId = matchedExistingCustomerId;
	}

	public UUID getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

	public ClientType getClientType() {
		return clientType;
	}

	public UUID getMatchedExistingCustomerId() {
		return matchedExistingCustomerId;
	}

}
