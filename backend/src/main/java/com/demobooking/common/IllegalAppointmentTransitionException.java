package com.demobooking.common;

import org.springframework.http.HttpStatus;

/** An appointment was asked to move to a status its current status can't reach - the state
 * moved under the caller. Maps to 409 - see {@link ApiExceptionHandler}. Deliberately not
 * IllegalStateException, so it can't be confused with an unrelated bug. */
public class IllegalAppointmentTransitionException extends ApiException {

	public IllegalAppointmentTransitionException(String message) {
		super(HttpStatus.CONFLICT, message);
	}

}
