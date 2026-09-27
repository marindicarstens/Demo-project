package com.demobooking.customer;

import com.demobooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * A failed existing-client directory match - always the same message regardless of which field(s)
 * mismatched. Maps to a 422 with suggestNewAccount - see {@link com.demobooking.common.ApiExceptionHandler}.
 */
public class DirectoryValidationException extends ApiException {

	public DirectoryValidationException(String message) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, message);
	}

	// suggestNewAccount is always true here - its presence must never be conditional on how close
	// the match was.
	@Override
	protected void addProperties(ProblemDetail problem) {
		problem.setProperty("suggestNewAccount", true);
	}

}
