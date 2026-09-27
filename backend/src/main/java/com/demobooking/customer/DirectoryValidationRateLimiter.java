package com.demobooking.customer;

import com.demobooking.common.RateLimiter;
import com.demobooking.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * A tighter-than-default per-IP ceiling on existing-client directory attempts specifically - this
 * one endpoint gets aggressive rate limiting since it's the one place a caller can test guesses
 * against real, if seeded, PII. Delegates to {@link RateLimiter} under its own bucket; a fixed
 * window, not exponential backoff - a deliberate simplification for this project's demo scope.
 */
// Public because booking calls it: Java's package-private access doesn't cover sibling packages.
@Component
public class DirectoryValidationRateLimiter {

	public static final String BUCKET = "directory-validation";
	private final RateLimiter rateLimiter;
	private final AppProperties.Limit limit;

	DirectoryValidationRateLimiter(RateLimiter rateLimiter, AppProperties appProperties) {
		this.rateLimiter = rateLimiter;
		this.limit = appProperties.rateLimit().directoryValidation();
	}

	/** Throws once the caller has exceeded app.rate-limit.directory-validation within its window;
	 * otherwise records this attempt. Fails closed: with Redis unreachable, this endpoint would
	 * otherwise be open to unlimited guessing, so the request is refused with a 503. */
	public void checkAndRecordAttempt(String clientIp) {
		rateLimiter.checkAndRecordAttemptOrRefuse(BUCKET, clientIp, limit.maxAttempts(), limit.window());
	}

}
