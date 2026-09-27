package com.demobooking.common;

import java.time.ZoneId;

/**
 * Every branch in this service is in South Africa, so "today"/"now" for availability, slot
 * generation, and appointment timing must be wall-clock SAST (UTC+2, no DST) - not whatever
 * timezone the JVM/host happens to default to (typically UTC in a container). Used to zone the
 * injected Clock and anywhere a date or time of day is derived from its instant, where
 * {@code LocalDate.now()}/{@code LocalTime.now()} would otherwise be ambiguous.
 */
public final class AppTimeZone {

	public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

	private AppTimeZone() {
	}

}
