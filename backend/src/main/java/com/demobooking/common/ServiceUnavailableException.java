package com.demobooking.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * 503 - a request that must not run without Redis (an idempotent create, an existing-client
 * directory check) arrived while Redis is unreachable. Retry-After tells the client when to try
 * again - see README.md § When Redis is down.
 */
public class ServiceUnavailableException extends ApiException {

	private static final String RETRY_AFTER_SECONDS = "5";

	public ServiceUnavailableException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, message);
	}

	@Override
	protected void addHeaders(HttpHeaders headers) {
		headers.set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
	}

}
