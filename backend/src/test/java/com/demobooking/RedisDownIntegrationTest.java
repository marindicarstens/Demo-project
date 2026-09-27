package com.demobooking;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.booking.dto.ExistingClientBookingRequest;
import com.demobooking.booking.dto.NewClientBookingRequest;
import com.demobooking.customer.ClientType;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The defined Redis-down mode (README.md § When Redis is down), with the real connection factory
 * pointed at a closed port - so the cache, the rate limiters and idempotency all lose Redis
 * together, as they would in an outage. Its own context, so the shared one keeps its Redis.
 */
@IntegrationTestWithoutRedis
class RedisDownIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@DynamicPropertySource
	static void unreachableRedis(DynamicPropertyRegistry registry) throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		registry.add("spring.data.redis.host", () -> "127.0.0.1");
		registry.add("spring.data.redis.port", () -> closedPort);
		registry.add("spring.data.redis.timeout", () -> "200ms");
		registry.add("spring.data.redis.connect-timeout", () -> "200ms");
	}

	@Test
	void cachedRead_fallsThroughToTheDatabase() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/branches", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void lookupRateLimit_failsOpen() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/v1/appointments/lookup?reference=BR-XXXXXX&email=a@b.c", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void idempotentBooking_failsClosedWithRetryAfter() {
		NewClientBookingRequest request = new NewClientBookingRequest(
				ClientType.NEW_CLIENT, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Redis Down", "redis.down@example.com", "+27825550400");
		HttpHeaders headers = new HttpHeaders();
		headers.set("Idempotency-Key", UUID.randomUUID().toString());

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/appointments", new HttpEntity<>(request, headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
	}

	@Test
	void directoryValidationRateLimit_failsClosed() {
		ExistingClientBookingRequest request = new ExistingClientBookingRequest(
				ClientType.EXISTING_CLIENT, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"thandiwe.demo@example.com", "9203015800082", "4051234567");

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/appointments", request, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
	}

}
