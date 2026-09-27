package com.demobooking.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Base for every domain exception that maps to a 4xx (or to a 503, for ServiceUnavailableException).
 * ApiExceptionHandler reads the status from here, so adding a new domain error never needs a new
 * handler.
 */
public abstract class ApiException extends RuntimeException {

	private final HttpStatus status;

	protected ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

	/** Extra problem+json properties. None by default, so most responses carry no properties at all. */
	protected void addProperties(ProblemDetail problem) {
	}

	/** Extra response headers. None by default. */
	protected void addHeaders(HttpHeaders headers) {
	}

}
