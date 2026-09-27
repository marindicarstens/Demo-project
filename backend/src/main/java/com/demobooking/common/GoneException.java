package com.demobooking.common;

import org.springframework.http.HttpStatus;

/** Maps to a 410 application/problem+json response - see {@link ApiExceptionHandler}. */
public class GoneException extends ApiException {

	public GoneException(String message) {
		super(HttpStatus.GONE, message);
	}

}
