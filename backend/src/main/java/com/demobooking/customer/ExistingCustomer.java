package com.demobooking.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A demo "known client" directory record - seed data only (V4/V9 migrations), never written
 * through any API. Stands in for a real core-banking lookup. See
 * docs/SEED-DATA.md § Existing-client demo directory.
 *
 * idNumber and accountNumber are plain strings: the directory is fictional seed data, so there
 * is no real secret to protect (see {@link DirectoryValidationService}). email is rendered back
 * and used for lookup, so it is plaintext too. phone is always null - none is seeded.
 */
@Entity
@Table(name = "existing_customer")
public class ExistingCustomer {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "full_name", nullable = false)
	private String fullName;

	@Column(nullable = false)
	private String email;

	@Column(name = "id_number", nullable = false)
	private String idNumber;

	@Column(name = "account_number", nullable = false)
	private String accountNumber;

	@Column(name = "phone")
	private String phone;

	protected ExistingCustomer() {
		// JPA
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

	public String getIdNumber() {
		return idNumber;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public String getPhone() {
		return phone;
	}

}
