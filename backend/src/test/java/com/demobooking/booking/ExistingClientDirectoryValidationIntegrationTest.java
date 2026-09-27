package com.demobooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import com.demobooking.booking.dto.AppointmentHoldResponse;
import com.demobooking.booking.dto.ExistingClientBookingRequest;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import com.demobooking.common.RateLimiter;
import com.demobooking.customer.ClientType;
import com.demobooking.customer.DirectoryValidationRateLimiter;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * The existing-client directory-validation flow. Covers the matched-record happy path,
 * the server-side client-type/service-type gate, and the two security properties that matter
 * most here: a byte-identical response across every mismatch reason, and the per-IP rate limit.
 */
@IntegrationTest
class ExistingClientDirectoryValidationIntegrationTest {

	// Seeded in V4__existing_client_directory.sql - see docs/SEED-DATA.md § Existing-client demo directory.
	private static final String SEEDED_NAME = "Thandiwe Nkosi";
	private static final String SEEDED_EMAIL = "thandiwe.demo@example.com";
	private static final String SEEDED_ID_NUMBER = "9203015800082";
	private static final String SEEDED_ACCOUNT_NUMBER = "4051234567";

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private Clock clock;

	@Autowired
	private StringRedisTemplate redis;

	@BeforeEach
	void resetDirectoryValidationRateLimit() {
		// Isolates each test from the others' attempt counts, regardless of which literal IP
		// string getRemoteAddr() resolves to on this machine (IPv4 vs IPv6 loopback).
		Set<String> keys = redis.keys(RateLimiter.keyFor(DirectoryValidationRateLimiter.BUCKET, "*"));
		if (keys != null && !keys.isEmpty()) {
			redis.delete(keys);
		}
	}

	@Test
	void matchedRecord_createsHoldWithNamePrefilledFromTheDirectory() {
		ResponseEntity<AppointmentHoldResponse> response = restTemplate.postForEntity(
				"/api/v1/appointments",
				existingClientRequestForAvailableSlot(SEEDED_EMAIL, SEEDED_ID_NUMBER, SEEDED_ACCOUNT_NUMBER),
				AppointmentHoldResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		AppointmentHoldResponse hold = response.getBody();
		assertThat(hold.status()).isEqualTo(AppointmentStatus.PENDING_CONFIRMATION);
		// The matched record's own name is used - the customer never retypes it.
		assertThat(hold.simulatedEmail().bodyText()).contains(SEEDED_NAME);
	}

	@Test
	void mismatchedFields_allReturnTheExactSameResponseRegardlessOfWhichFieldWasWrong() {
		ProblemDetail wrongEmail = failedValidationBody("nobody@example.com", SEEDED_ID_NUMBER, SEEDED_ACCOUNT_NUMBER);
		ProblemDetail wrongId = failedValidationBody(SEEDED_EMAIL, "1111111111111", SEEDED_ACCOUNT_NUMBER);
		ProblemDetail wrongAccount = failedValidationBody(SEEDED_EMAIL, SEEDED_ID_NUMBER, "0000000000");
		ProblemDetail allWrong = failedValidationBody("nobody@example.com", "1111111111111", "0000000000");

		// Every dimension of the response - title, detail, status, and the suggestNewAccount flag
		// - must be identical no matter which (or how many) fields actually mismatched. See
		// This endpoint is a PII-matching oracle by design, and any
		// difference here would let an attacker enumerate which field was right.
		assertThat(wrongId).usingRecursiveComparison().isEqualTo(wrongEmail);
		assertThat(wrongAccount).usingRecursiveComparison().isEqualTo(wrongEmail);
		assertThat(allWrong).usingRecursiveComparison().isEqualTo(wrongEmail);
		assertThat(wrongEmail.getProperties()).containsEntry("suggestNewAccount", true);
	}

	@Test
	void unknownEmail_alsoReturnsTheExactSameResponse() {
		// No EXISTING_CUSTOMER row for this email at all - not just a wrong ID/account against a
		// real row. Must be indistinguishable from every other mismatch case too.
		ProblemDetail unknownEmail = failedValidationBody("not-in-the-directory@example.com", SEEDED_ID_NUMBER, SEEDED_ACCOUNT_NUMBER);
		ProblemDetail wrongEmail = failedValidationBody("nobody@example.com", SEEDED_ID_NUMBER, SEEDED_ACCOUNT_NUMBER);

		assertThat(unknownEmail).usingRecursiveComparison().isEqualTo(wrongEmail);
	}

	@Test
	void serviceTypeNotApplicableToExistingClientFlow_returns422EvenWithAMatchedRecord() {
		// "Open a new account" is New-Account-only (docs/SEED-DATA.md) - submitting it under
		// clientType=EXISTING_CLIENT must be rejected server-side even with valid directory
		// credentials, since the client-chosen flow is a parameter the server must not trust
		// blindly. Filtered to strictly NEW_CLIENT (not BOTH), since the
		// listing endpoint also includes BOTH-tagged types for this clientType.
		ServiceTypeResponse[] newClientTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);
		UUID newClientOnlyServiceTypeId = Arrays.stream(newClientTypes)
				.filter(type -> type.applicableClientType().name().equals("NEW_CLIENT"))
				.findFirst()
				.orElseThrow()
				.id();
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		UUID branchId = branches[0].id();
		UUID slotId = firstAvailableSlotId(branchId, newClientOnlyServiceTypeId);

		ExistingClientBookingRequest request = new ExistingClientBookingRequest(
				ClientType.EXISTING_CLIENT, branchId, newClientOnlyServiceTypeId, slotId, SEEDED_EMAIL, SEEDED_ID_NUMBER, SEEDED_ACCOUNT_NUMBER);
		ResponseEntity<ProblemDetail> response = restTemplate.postForEntity("/api/v1/appointments", request, ProblemDetail.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		// Distinguishable from a directory mismatch - this is a different failure reason, so it's
		// fine (and correct) for it not to carry suggestNewAccount. No properties at all here
		// (getProperties() is null, not an empty map) since UnprocessableEntityException's handler
		// never adds any - only DirectoryValidationException's does.
		assertThat(response.getBody().getProperties()).isNull();
	}

	@Test
	void sixthFailedAttemptFromTheSameCallerIsRateLimited() {
		for (int attempt = 1; attempt <= 5; attempt++) {
			ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
					"/api/v1/appointments",
					existingClientRequestForAvailableSlot("nobody@example.com", SEEDED_ID_NUMBER, SEEDED_ACCOUNT_NUMBER),
					ProblemDetail.class);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		}

		ResponseEntity<ProblemDetail> sixthAttempt = restTemplate.postForEntity(
				"/api/v1/appointments",
				existingClientRequestForAvailableSlot("nobody@example.com", SEEDED_ID_NUMBER, SEEDED_ACCOUNT_NUMBER),
				ProblemDetail.class);
		assertThat(sixthAttempt.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	private ProblemDetail failedValidationBody(String email, String idNumber, String accountNumber) {
		ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
				"/api/v1/appointments", existingClientRequestForAvailableSlot(email, idNumber, accountNumber), ProblemDetail.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		return response.getBody();
	}

	private ExistingClientBookingRequest existingClientRequestForAvailableSlot(String email, String idNumber, String accountNumber) {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		UUID branchId = branches[0].id();
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=EXISTING_CLIENT", ServiceTypeResponse[].class);
		UUID serviceTypeId = serviceTypes[0].id();
		UUID slotId = firstAvailableSlotId(branchId, serviceTypeId);
		return new ExistingClientBookingRequest(ClientType.EXISTING_CLIENT, branchId, serviceTypeId, slotId, email, idNumber, accountNumber);
	}

	private UUID firstAvailableSlotId(UUID branchId, UUID serviceTypeId) {
		return BookingFixtures.firstAvailableSlotId(restTemplate, clock, branchId, serviceTypeId);
	}

}
