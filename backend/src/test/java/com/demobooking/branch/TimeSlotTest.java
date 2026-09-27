package com.demobooking.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demobooking.common.SlotFullException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeSlotTest {

	private static TimeSlot withCapacity(int capacity) {
		return new TimeSlot(null, null, LocalDate.of(2026, 3, 2), LocalTime.of(9, 0), capacity);
	}

	@Test
	void reserve_countsUpToCapacityThenThrows() {
		TimeSlot slot = withCapacity(2);

		slot.reserve();
		slot.reserve();

		assertThat(slot.getBookedCount()).isEqualTo(2);
		assertThat(slot.getRemainingCapacity()).isZero();
		assertThatThrownBy(slot::reserve).isInstanceOf(SlotFullException.class);
		assertThat(slot.getBookedCount()).isEqualTo(2);
	}

	@Test
	void release_freesOneReservationAndNeverGoesBelowZero() {
		TimeSlot slot = withCapacity(1);
		slot.reserve();

		slot.release();
		assertThat(slot.getBookedCount()).isZero();

		slot.release();
		assertThat(slot.getBookedCount()).isZero();
		assertThat(slot.getRemainingCapacity()).isEqualTo(1);
	}

}
