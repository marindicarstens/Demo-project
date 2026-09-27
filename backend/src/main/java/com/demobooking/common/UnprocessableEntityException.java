package com.demobooking.common;

import org.springframework.http.HttpStatus;

/** Maps to a 422 application/problem+json response - see {@link ApiExceptionHandler}. */
public class UnprocessableEntityException extends ApiException {

	public UnprocessableEntityException(String message) {
		// UNPROCESSABLE_CONTENT, not the deprecated UNPROCESSABLE_ENTITY - RFC 9110 renamed 422.
		super(HttpStatus.UNPROCESSABLE_CONTENT, message);
	}

}
