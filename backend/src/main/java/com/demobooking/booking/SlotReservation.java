package com.demobooking.booking;

import com.demobooking.common.ConflictException;
import com.demobooking.common.SlotFullException;
import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;

/** Booking a hold and requesting a reschedule both claim slot capacity, and report failing to get it the same way. */
final class SlotReservation {

	private SlotReservation() {
	}

	/** Runs the reservation; a full slot, or a race still lost after every retry, becomes a 409. */
	static <T> T reserveOrConflict(Supplier<T> reservation) {
		try {
			return reservation.get();
		} catch (OptimisticLockingFailureException | SlotFullException noCapacity) {
			throw new ConflictException("Requested slot has no remaining capacity", ConflictException.SLOT_FULL);
		}
	}

}
