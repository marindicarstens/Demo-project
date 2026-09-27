package com.demobooking.booking;

import com.demobooking.branch.TimeSlot;
import com.demobooking.common.AppTimeZone;
import java.time.Instant;
import java.time.ZonedDateTime;

/** When a time slot's date+time actually occurs, in wall-clock terms - shared by BookingService
 * (the cancellation token's expiry) and RescheduleService (the reschedule minimum-notice
 * window). */
final class AppointmentTimes {

	private AppointmentTimes() {
	}

	static Instant startInstant(TimeSlot slot) {
		return ZonedDateTime.of(slot.getSlotDate(), slot.getStartTime(), AppTimeZone.ZONE).toInstant();
	}

}
