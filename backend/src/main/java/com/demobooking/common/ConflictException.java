package com.demobooking.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Maps to a 409 application/problem+json response - see {@link ApiExceptionHandler}. */
public class ConflictException extends ApiException {

	/** The code of a 409 for a slot with no remaining capacity. Clients match on it, not on the
	 * detail text, which is free to change. */
	public static final String SLOT_FULL = "SLOT_FULL";

	private final String code;

	public ConflictException(String message) {
		this(message, null);
	}

	/** @param code a stable, machine-readable reason, sent as the problem's {@code code} property; null for none */
	public ConflictException(String message, String code) {
		super(HttpStatus.CONFLICT, message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	@Override
	protected void addProperties(ProblemDetail problem) {
		if (code != null) {
			problem.setProperty("code", code);
		}
	}

}
