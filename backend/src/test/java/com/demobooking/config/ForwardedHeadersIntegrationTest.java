package com.demobooking.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import com.demobooking.common.RateLimiter;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * The untrusted case: X-Forwarded-For from anyone but the configured proxy is ignored, so a
 * caller can't escape its rate-limit bucket by inventing a new client address per request. Only
 * an address no test client can have is trusted here, so the loopback test client is untrusted.
 * The trusted case is TrustedProxyRemoteIpValveTest.
 */
@IntegrationTest
@TestPropertySource(properties = {
		"server.tomcat.remoteip.internal-proxies=10\\.255\\.255\\.254",
		"app.rate-limit.lookup.max-attempts=3"})
class ForwardedHeadersIntegrationTest {

	private static final String LOOKUP_URL = "/api/v1/appointments/lookup?reference=BR-XXXXXX&email=a@b.c";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private StringRedisTemplate redis;

	// The shared Redis already holds this bucket's counts from other test classes' lookups.
	@BeforeEach
	@AfterEach
	void clearLookupBucket() {
		Set<String> keys = redis.keys(RateLimiter.keyFor("appointment-lookup", "*"));
		if (!keys.isEmpty()) {
			redis.delete(keys);
		}
	}

	@Test
	void spoofedForwardedFor_fromUntrustedClient_isIgnored() {
		for (int i = 0; i < 3; i++) {
			assertThat(lookupAs("203.0.113.1").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		}

		// A different claimed client still lands in the same (loopback) bucket.
		assertThat(lookupAs("203.0.113.2").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	private ResponseEntity<String> lookupAs(String forwardedFor) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Forwarded-For", forwardedFor);
		return restTemplate.exchange(LOOKUP_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

}
