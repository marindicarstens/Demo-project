package com.demobooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.branch.TimeSlot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Slot times are wall-clock times in the branch's zone, so the instant must carry that offset. */
class AppointmentTimesTest {

	@Test
	void startInstant_readsTheSlotInTheBranchTimeZone() {
		TimeSlot slot = new TimeSlot(null, null, LocalDate.of(2026, 3, 2), LocalTime.of(9, 30), 1);

		// Johannesburg is UTC+2 all year (no daylight saving).
		assertThat(AppointmentTimes.startInstant(slot)).isEqualTo(Instant.parse("2026-03-02T07:30:00Z"));
	}

}
