package com.demobooking.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error response is application/problem+json (RFC 7807). Spring's own ProblemDetail
 * type already produces that shape, so handlers just build one.
 *
 * Extends ResponseEntityExceptionHandler so every Spring MVC exception (bad JSON, a malformed
 * path variable, a missing parameter, an unknown route, a wrong method, ...) keeps its own
 * 4xx status instead of falling through to the 500 catch-all below.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	// Adds errors: {field: message} so a client can point at the offending field. Only the first
	// message per field is kept - a map, not a list, keeps the shape simple for the frontend.
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		ProblemDetail body = ex.getBody();
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	// One handler for every domain error - the status (and any extra properties or headers, such
	// as DirectoryValidationException's suggestNewAccount or ServiceUnavailableException's
	// Retry-After) come from the exception itself.
	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		ex.addProperties(problem);
		HttpHeaders headers = new HttpHeaders();
		ex.addHeaders(headers);
		return ResponseEntity.status(ex.getStatus()).headers(headers).body(problem);
	}

	// An @Version (Appointment's or Notification's) lost a race with a concurrent writer
	// (typically ExpirySweepService, see those classes' Javadoc) - the request itself was otherwise valid, so this is a
	// "the state moved under you" conflict, same family as ConflictException, not a client error.
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
		return ProblemDetail.forStatusAndDetail(
				HttpStatus.CONFLICT, "This appointment was modified at the same time by something else - please refresh and try again");
	}

	// Only true server faults reach here - client-input errors are handled by the superclass.
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
	}

}
