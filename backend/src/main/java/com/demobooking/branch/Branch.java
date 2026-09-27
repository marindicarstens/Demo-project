package com.demobooking.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "branch")
public class Branch {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String address;

	@Column(nullable = false)
	private String city;

	private String phone;

	@Column(name = "opens_at", nullable = false)
	private LocalTime opensAt;

	@Column(name = "closes_at", nullable = false)
	private LocalTime closesAt;

	@Column(nullable = false)
	private boolean active = true;

	protected Branch() {
		// JPA
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getAddress() {
		return address;
	}

	public String getCity() {
		return city;
	}

	public String getPhone() {
		return phone;
	}

	public LocalTime getOpensAt() {
		return opensAt;
	}

	public LocalTime getClosesAt() {
		return closesAt;
	}

	public boolean isActive() {
		return active;
	}

}
