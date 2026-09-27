package com.demobooking.booking;

import com.demobooking.branch.TimeSlot;
import com.demobooking.common.IllegalAppointmentTransitionException;
import com.demobooking.common.UnprocessableEntityException;
import com.demobooking.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code version} makes a confirm and the expiry sweep racing on one appointment conflict, so it
 * can never end up CONFIRMED after the sweep released its slot. Each transition also checks the
 * current status, so a stale caller can't apply one that status doesn't allow.
 */
@Entity
@Table(name = "appointment")
public class Appointment {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "time_slot_id")
	private TimeSlot timeSlot;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id")
	private Customer customer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AppointmentStatus status;

	@Column(name = "reference_code", nullable = false, unique = true)
	private String referenceCode;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Version
	private long version;

	protected Appointment() {
		// JPA
	}

	public Appointment(TimeSlot timeSlot, Customer customer, String referenceCode, Instant createdAt) {
		this.timeSlot = timeSlot;
		this.customer = customer;
		this.referenceCode = referenceCode;
		this.status = AppointmentStatus.PENDING_CONFIRMATION;
		this.createdAt = createdAt;
	}

	/** Transitions PENDING_CONFIRMATION -> CONFIRMED. */
	public void confirm() {
		transition(AppointmentStatus.PENDING_CONFIRMATION, AppointmentStatus.CONFIRMED);
	}

	/** Transitions PENDING_CONFIRMATION -> EXPIRED. Called only by the sweep job. */
	public void markExpired() {
		transition(AppointmentStatus.PENDING_CONFIRMATION, AppointmentStatus.EXPIRED);
	}

	/** Transitions CONFIRMED -> CANCELLED. */
	public void cancel() {
		transition(AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED);
	}

	private void transition(AppointmentStatus requiredCurrent, AppointmentStatus next) {
		if (status != requiredCurrent) {
			throw new IllegalAppointmentTransitionException(
					"Appointment " + referenceCode + " cannot move from " + status + " to " + next);
		}
		this.status = next;
	}

	/**
	 * The customer-facing pre-check before a reschedule or cancel: only a CONFIRMED appointment
	 * can be modified. A 422, because the customer asked for something its status doesn't allow;
	 * the transition guards' 409 is reserved for a caller acting on a stale read.
	 */
	public void requireModifiable() {
		if (status != AppointmentStatus.CONFIRMED) {
			throw new UnprocessableEntityException("This appointment can no longer be modified");
		}
	}

	/** Points this CONFIRMED appointment at its new slot - the last step of the two-phase
	 * reschedule (request, then confirm), not an atomic move on its own. The caller releases the
	 * old slot in the same transaction. */
	public void reschedule(TimeSlot newSlot) {
		if (status != AppointmentStatus.CONFIRMED) {
			throw new IllegalAppointmentTransitionException("Appointment " + referenceCode + " cannot be rescheduled while " + status);
		}
		this.timeSlot = newSlot;
	}

	public UUID getId() {
		return id;
	}

	public TimeSlot getTimeSlot() {
		return timeSlot;
	}

	public Customer getCustomer() {
		return customer;
	}

	public AppointmentStatus getStatus() {
		return status;
	}

	public String getReferenceCode() {
		return referenceCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
