package com.demobooking.common;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * A per-IP fixed-window counter in Redis, shared by every instance. A fixed window rather than
 * backoff keeps the limit simple to reason about. With Redis down, {@link #checkAndRecordAttempt}
 * lets the request through (a lost limit beats a lost booking) and
 * {@link #checkAndRecordAttemptOrRefuse} refuses it with a 503 - see README.md § When Redis is down.
 */
@Component
public class RateLimiter {

	// INCR and the first-hit PEXPIRE run as one script: as two calls, a failure between them would
	// leave a counter with no TTL, locking that caller out for good once it reached the ceiling.
	private static final RedisScript<Long> INCREMENT_WITH_WINDOW = new DefaultRedisScript<>(
			"local count = redis.call('INCR', KEYS[1]) "
					+ "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
					+ "return count",
			Long.class);

	private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

	private final StringRedisTemplate redis;

	RateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** Throws {@link TooManyAttemptsException} once the caller has exceeded {@code maxAttempts}
	 * within {@code window} for this bucket+clientIp; otherwise records this attempt. {@code
	 * bucket} namespaces independent limits (e.g. "booking-create", "appointment-lookup") so they
	 * never share a counter. Fails open: if Redis is unreachable, the attempt is allowed. */
	public void checkAndRecordAttempt(String bucket, String clientIp, long maxAttempts, Duration window) {
		Long count;
		try {
			count = increment(bucket, clientIp, window);
		} catch (RedisConnectionFailureException e) {
			log.warn("Redis is unreachable, so the '{}' rate limit is not enforced: {}", bucket, e.getMessage());
			return;
		}
		rejectIfOver(count, maxAttempts);
	}

	/** Same as {@link #checkAndRecordAttempt}, but fails closed: if Redis is unreachable, the
	 * request is refused with {@link ServiceUnavailableException} instead of going unlimited. */
	public void checkAndRecordAttemptOrRefuse(String bucket, String clientIp, long maxAttempts, Duration window) {
		Long count;
		try {
			count = increment(bucket, clientIp, window);
		} catch (RedisConnectionFailureException e) {
			log.warn("Redis is unreachable, so requests limited by '{}' are refused: {}", bucket, e.getMessage());
			throw new ServiceUnavailableException("This service is temporarily unavailable - please try again shortly");
		}
		rejectIfOver(count, maxAttempts);
	}

	private Long increment(String bucket, String clientIp, Duration window) {
		return redis.execute(INCREMENT_WITH_WINDOW, List.of(keyFor(bucket, clientIp)), String.valueOf(window.toMillis()));
	}

	private static void rejectIfOver(Long count, long maxAttempts) {
		if (count != null && count > maxAttempts) {
			throw new TooManyAttemptsException("Too many attempts - please try again later");
		}
	}

	/** The Redis key a bucket+clientIp counts under - exposed so tests can inspect or reset it. */
	public static String keyFor(String bucket, String clientIp) {
		return "rate-limit:" + bucket + ":" + clientIp;
	}

}
