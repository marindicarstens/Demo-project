package com.demobooking.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** A time slot has no remaining capacity. Its own type so callers can map exactly this to "slot
 * full", while any other IllegalStateException still surfaces as the bug it is (a 500). Callers
 * translate it to a ConflictException; 409 is only the fallback if one ever escapes. */
public class SlotFullException extends ApiException {

	public SlotFullException(String message) {
		super(HttpStatus.CONFLICT, message);
	}

	@Override
	protected void addProperties(ProblemDetail problem) {
		problem.setProperty("code", ConflictException.SLOT_FULL);
	}

}
