package com.demobooking;

import com.demobooking.common.AppTimeZone;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Real time plus an offset tests can push forward. It keeps ticking rather than freezing, so code
 * that orders rows by time still sees them in order; {@link #reset()} drops the offset.
 */
public class MutableClock extends Clock {

	private volatile Duration offset = Duration.ZERO;

	public void advance(Duration amount) {
		offset = offset.plus(amount);
	}

	public void reset() {
		offset = Duration.ZERO;
	}

	@Override
	public Instant instant() {
		return Instant.now().plus(offset);
	}

	@Override
	public ZoneId getZone() {
		return AppTimeZone.ZONE;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("The app runs on one zone - see AppTimeZone");
	}

}
