package com.demobooking.common;

import org.springframework.http.HttpStatus;

/** 429 - any rate limit (RateLimiter or DirectoryValidationRateLimiter) - see {@link ApiExceptionHandler}. */
public class TooManyAttemptsException extends ApiException {

	public TooManyAttemptsException(String message) {
		super(HttpStatus.TOO_MANY_REQUESTS, message);
	}

}
