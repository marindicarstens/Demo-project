package com.demobooking.branch;

import com.demobooking.common.SlotFullException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A bookable slot that counts its own reservations ({@link #reserve}, {@link #release}).
 * {@code version} turns two concurrent reservations into a detectable conflict the caller can
 * retry; the database CHECK (booked_count <= capacity) is the last line of defence.
 */
@Entity
@Table(name = "time_slot")
public class TimeSlot {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private Branch branch;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private ServiceType serviceType;

	@Column(name = "slot_date", nullable = false)
	private LocalDate slotDate;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private int capacity;

	@Column(name = "booked_count", nullable = false)
	private int bookedCount = 0;

	@Version
	private long version;

	protected TimeSlot() {
		// JPA
	}

	public TimeSlot(Branch branch, ServiceType serviceType, LocalDate slotDate, LocalTime startTime, int capacity) {
		this.branch = branch;
		this.serviceType = serviceType;
		this.slotDate = slotDate;
		this.startTime = startTime;
		this.capacity = capacity;
	}

	public UUID getId() {
		return id;
	}

	public Branch getBranch() {
		return branch;
	}

	public ServiceType getServiceType() {
		return serviceType;
	}

	public LocalDate getSlotDate() {
		return slotDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public int getCapacity() {
		return capacity;
	}

	public int getBookedCount() {
		return bookedCount;
	}

	public int getRemainingCapacity() {
		return capacity - bookedCount;
	}

	/** Throws rather than returning false, so a caller can't accidentally ignore a full slot. */
	public void reserve() {
		if (getRemainingCapacity() <= 0) {
			throw new SlotFullException("Slot " + id + " has no remaining capacity");
		}
		bookedCount++;
	}

	/** Releases one reservation - cancellation, reschedule-away, or an expired unconfirmed hold. */
	public void release() {
		bookedCount = Math.max(0, bookedCount - 1);
	}

}
