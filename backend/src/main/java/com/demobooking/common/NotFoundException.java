package com.demobooking.common;

import org.springframework.http.HttpStatus;

/** Maps to a 404 application/problem+json response - see {@link ApiExceptionHandler}. */
public class NotFoundException extends ApiException {

	public NotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, message);
	}

}
