package com.demobooking.booking;

import com.demobooking.common.SecureTokens;
import com.demobooking.common.ServiceUnavailableException;
import com.demobooking.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis-backed, with its own TTL (app.booking.idempotency-key-ttl, defaulting to the confirmation
 * hold window); a body-hash mismatch is a 422 rather than silently overwriting.
 *
 * The one deliberate exception to "only token hashes are stored": a completed record holds the
 * original response, whose actionLink carries the raw confirmation token. A replay must return
 * that link, since it is the retrying client's only way to confirm. The exposure is bounded -
 * AppProperties rejects a record TTL longer than the token's own validity, so a record never
 * outlives the token it contains.
 *
 * Fails closed: if Redis is unreachable, {@link #claim} refuses the request with a 503 rather than
 * creating a hold whose retry could not be recognised as a duplicate.
 */
@Service
class IdempotencyService {

	private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

	private static final String KEY_PREFIX = "idempotency:appointments:";
	// Not valid StoredRecord JSON, so claim() can tell "someone else is mid-request" apart from a
	// finished record without a second Redis key or data structure.
	private static final String IN_PROGRESS_MARKER = "IN_PROGRESS";

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final Duration ttl;

	IdempotencyService(StringRedisTemplate redis, ObjectMapper objectMapper, AppProperties appProperties) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.ttl = appProperties.booking().idempotencyKeyTtl();
	}

	// ignoreUnknown: a record written before the unused "status" field was dropped still reads.
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record StoredRecord(String requestHash, String responseBodyJson) {
	}

	public enum ClaimStatus {
		/** Nothing was stored for this key - the caller now owns it, and must follow up with
		 * either {@link #store} (on success) or {@link #release} (on failure). */
		CLAIMED,
		/** A different request with this same key is still being processed - genuinely
		 * concurrent, not a replay; the caller should not create a second hold. */
		IN_PROGRESS,
		/** A finished record already exists - replay it (same body) or reject it (different
		 * body), never create a new hold. */
		COMPLETED
	}

	public record ClaimResult(ClaimStatus status, StoredRecord existing) {

		static ClaimResult claimed() {
			return new ClaimResult(ClaimStatus.CLAIMED, null);
		}

		static ClaimResult inProgress() {
			return new ClaimResult(ClaimStatus.IN_PROGRESS, null);
		}

		static ClaimResult completed(StoredRecord record) {
			return new ClaimResult(ClaimStatus.COMPLETED, record);
		}
	}

	/** A stable fingerprint of the request body, used to detect "same key, different body". */
	public String hashOf(Object requestBody) {
		return SecureTokens.hash(objectMapper.writeValueAsString(requestBody));
	}

	/**
	 * Atomically claims the key via Redis {@code SET ... NX} (a plain GET-then-SET has a real gap
	 * here: two requests sharing a key can both read "nothing stored yet" and both proceed to
	 * create a hold, defeating the whole point of the key). Whichever caller's {@code setIfAbsent}
	 * actually wins becomes {@code CLAIMED} and owns this key until it calls {@link #store} or
	 * {@link #release}; every other concurrent caller sees {@code IN_PROGRESS} (the winner hasn't
	 * finished yet) or {@code COMPLETED} (it has).
	 */
	public ClaimResult claim(String idempotencyKey) {
		String key = KEY_PREFIX + idempotencyKey;
		Boolean won;
		try {
			won = redis.opsForValue().setIfAbsent(key, IN_PROGRESS_MARKER, ttl);
		} catch (RedisConnectionFailureException e) {
			log.warn("Redis is unreachable, so a request with an Idempotency-Key is refused: {}", e.getMessage());
			throw new ServiceUnavailableException("Bookings are temporarily unavailable - please try again shortly");
		}
		if (Boolean.TRUE.equals(won)) {
			return ClaimResult.claimed();
		}
		String raw = redis.opsForValue().get(key);
		if (raw == null || IN_PROGRESS_MARKER.equals(raw)) {
			// null here means the winner's own claim already expired/was released between our
			// failed setIfAbsent and this read - vanishingly unlikely at these TTLs, but treated
			// the same as still-in-progress rather than silently falling through to a third state.
			return ClaimResult.inProgress();
		}
		return ClaimResult.completed(objectMapper.readValue(raw, StoredRecord.class));
	}

	/** Publishes the real result over the IN_PROGRESS marker - call only after {@link #claim}
	 * returns {@code CLAIMED}. */
	public void store(String idempotencyKey, StoredRecord record) {
		redis.opsForValue().set(KEY_PREFIX + idempotencyKey, objectMapper.writeValueAsString(record), ttl);
	}

	/** Releases a claimed key without publishing a result - call only after {@link #claim}
	 * returns {@code CLAIMED} and the attempt that followed then failed (e.g. the slot turned out
	 * to be full). Without this, a legitimate retry of a failed attempt would be blocked behind
	 * its own stale IN_PROGRESS marker for the rest of the TTL. */
	public void release(String idempotencyKey) {
		redis.delete(KEY_PREFIX + idempotencyKey);
	}

}
