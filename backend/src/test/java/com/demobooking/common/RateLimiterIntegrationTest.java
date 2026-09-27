package com.demobooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/** The counter and its window must be set together - a key without a TTL never resets. */
@IntegrationTest
class RateLimiterIntegrationTest {

	@Autowired
	private RateLimiter rateLimiter;

	@Autowired
	private StringRedisTemplate redis;

	@Test
	void firstAttempt_setsTtl() {
		String clientIp = UUID.randomUUID().toString();
		String key = RateLimiter.keyFor("ttl-test", clientIp);
		try {
			rateLimiter.checkAndRecordAttempt("ttl-test", clientIp, 5, Duration.ofMinutes(15));

			assertThat(redis.opsForValue().get(key)).isEqualTo("1");
			assertThat(redis.getExpire(key)).isPositive();
		} finally {
			redis.delete(key);
		}
	}

}
