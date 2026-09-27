package com.demobooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import com.demobooking.booking.dto.NewClientBookingRequest;
import com.demobooking.customer.ClientType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Bad client input must come back as a 4xx problem+json, never the 500 catch-all. Every request
 * here fails before any controller body runs, so none of them touch data or the rate limits.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ApiErrorContractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void malformedPathUuid_returns400WithoutAnErrorLog(CapturedOutput output) {
		ResponseEntity<ProblemDetail> response =
				restTemplate.exchange("/api/v1/appointments/not-a-uuid", HttpMethod.DELETE, null, ProblemDetail.class);

		assertBadRequestProblem(response);
		// The 500 catch-all logs at ERROR - reaching it at all would mean the mapping regressed.
		assertThat(output).doesNotContain("Unhandled exception");
	}

	@Test
	void unparseableJsonBody_returns400() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
				"/api/v1/appointments", new HttpEntity<>("{bad json", headers), ProblemDetail.class);

		assertBadRequestProblem(response);
	}

	@Test
	void invalidField_returns400WithTheFieldNamedInErrors() {
		NewClientBookingRequest request = new NewClientBookingRequest(
				ClientType.NEW_CLIENT, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Test Person", "test@example.com", "123");

		ResponseEntity<ProblemDetail> response = restTemplate.postForEntity("/api/v1/appointments", request, ProblemDetail.class);

		assertBadRequestProblem(response);
		assertThat(response.getBody().getProperties()).containsKey("errors");
		@SuppressWarnings("unchecked")
		Map<String, Object> errors = (Map<String, Object>) response.getBody().getProperties().get("errors");
		assertThat(errors).containsOnlyKeys("phone");
	}

	@Test
	void missingRequiredQueryParameter_returns400() {
		ResponseEntity<ProblemDetail> response =
				restTemplate.getForEntity("/api/v1/appointments/lookup?reference=X", ProblemDetail.class);

		assertBadRequestProblem(response);
	}

	@Test
	void unknownRoute_returns404() {
		ResponseEntity<ProblemDetail> response = restTemplate.getForEntity("/api/v1/nope", ProblemDetail.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void getWithMalformedPathUuid_returns400() {
		// /appointments/{id} now maps GET too, so a bad id there is bad input, not a wrong method.
		ResponseEntity<ProblemDetail> response =
				restTemplate.getForEntity("/api/v1/appointments/not-a-uuid", ProblemDetail.class);

		assertBadRequestProblem(response);
	}

	@Test
	void unsupportedMethod_returns405() {
		// /branches maps GET only, so no other handler can claim a PUT and turn this into a 400.
		ResponseEntity<ProblemDetail> response =
				restTemplate.exchange("/api/v1/branches", HttpMethod.PUT, HttpEntity.EMPTY, ProblemDetail.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
	}

	private static void assertBadRequestProblem(ResponseEntity<ProblemDetail> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(response.getBody().getStatus()).isEqualTo(400);
	}

}
