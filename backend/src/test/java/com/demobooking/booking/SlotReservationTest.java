package com.demobooking.booking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demobooking.common.ConflictException;
import com.demobooking.common.SlotFullException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class SlotReservationTest {

	@Test
	void fullSlot_becomesAConflictWithTheSlotFullCode() {
		assertThatThrownBy(() -> SlotReservation.reserveOrConflict(() -> {
			throw new SlotFullException("full");
		}))
				.isInstanceOf(ConflictException.class)
				.extracting(e -> ((ConflictException) e).getCode())
				.isEqualTo(ConflictException.SLOT_FULL);
	}

	@Test
	void raceLostAfterEveryRetry_becomesAConflictWithTheSlotFullCode() {
		assertThatThrownBy(() -> SlotReservation.reserveOrConflict(() -> {
			throw new OptimisticLockingFailureException("lost");
		}))
				.isInstanceOf(ConflictException.class)
				.extracting(e -> ((ConflictException) e).getCode())
				.isEqualTo(ConflictException.SLOT_FULL);
	}

}
