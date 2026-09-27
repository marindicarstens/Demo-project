package com.demobooking.booking;

import static com.demobooking.booking.BookingFixtures.extractToken;
import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import com.demobooking.booking.dto.AppointmentHoldResponse;
import com.demobooking.booking.dto.ConfirmationResultResponse;
import com.demobooking.booking.dto.NewClientBookingRequest;
import com.demobooking.branch.dto.AvailabilitySlotResponse;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import com.demobooking.common.AppTimeZone;
import com.demobooking.customer.ClientType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Full New Account booking lifecycle against real Postgres + Redis (Testcontainers). Covers
 * the golden path plus the error cases named in
 * docs/api/openapi.yaml: unknown/expired token (404/410), duplicate Idempotency-Key with a
 * different body (422).
 */
@IntegrationTest
class AppointmentLifecycleIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private Clock clock;

	@Test
	void holdThenConfirm_confirmsTheAppointmentAndIssuesAnAccessToken() {
		NewClientBookingRequest request = newClientRequestForAvailableSlot();

		ResponseEntity<AppointmentHoldResponse> holdResponse =
				restTemplate.postForEntity("/api/v1/appointments", request, AppointmentHoldResponse.class);
		assertThat(holdResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		AppointmentHoldResponse hold = holdResponse.getBody();
		assertThat(hold.status()).isEqualTo(AppointmentStatus.PENDING_CONFIRMATION);
		assertThat(hold.referenceCode()).startsWith("BR-");
		String rawToken = extractToken(hold.simulatedEmail().actionLink());

		ResponseEntity<ConfirmationResultResponse> confirmResponse =
				restTemplate.postForEntity("/api/v1/confirmations/{token}", null, ConfirmationResultResponse.class, rawToken);
		assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		ConfirmationResultResponse confirmed = confirmResponse.getBody();
		assertThat(confirmed.status()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(confirmed.accessToken()).isNotBlank();
		assertThat(confirmed.referenceCode()).isEqualTo(hold.referenceCode());
		assertThat(confirmed.receiptEmail().bodyText()).contains(hold.referenceCode());
	}

	@Test
	void confirm_unknownToken_returns404() {
		ResponseEntity<ProblemDetail> response =
				restTemplate.postForEntity("/api/v1/confirmations/{token}", null, ProblemDetail.class, "not-a-real-token");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void confirm_reusedToken_returns410Gone() {
		NewClientBookingRequest request = newClientRequestForAvailableSlot();
		AppointmentHoldResponse hold = restTemplate.postForObject("/api/v1/appointments", request, AppointmentHoldResponse.class);
		String rawToken = extractToken(hold.simulatedEmail().actionLink());

		restTemplate.postForEntity("/api/v1/confirmations/{token}", null, ConfirmationResultResponse.class, rawToken);

		ResponseEntity<ProblemDetail> secondAttempt =
				restTemplate.postForEntity("/api/v1/confirmations/{token}", null, ProblemDetail.class, rawToken);
		assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.GONE);
	}

	@Test
	void createAppointment_sameIdempotencyKeyDifferentBody_returns422() {
		NewClientBookingRequest firstRequest = newClientRequestForAvailableSlot();
		HttpHeaders headers = new HttpHeaders();
		headers.set("Idempotency-Key", "test-key-" + UUID.randomUUID());
		restTemplate.exchange("/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(firstRequest, headers), AppointmentHoldResponse.class);

		// Genuinely different from firstRequest (not just a second, possibly-identical lookup) -
		// same slot/branch/service, different customer details.
		NewClientBookingRequest differentRequest = new NewClientBookingRequest(
				ClientType.NEW_CLIENT, firstRequest.branchId(), firstRequest.serviceTypeId(), firstRequest.slotId(), "A Different Name", "different@example.com", "+27825550000");
		ResponseEntity<ProblemDetail> secondResponse = restTemplate.exchange(
				"/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(differentRequest, headers), ProblemDetail.class);

		assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
	}

	@Test
	void bookedSlot_disappearsFromAvailability_andASecondBookingAttemptIsRejected() {
		NewClientBookingRequest request = newClientRequestForAvailableSlot();
		restTemplate.postForObject("/api/v1/appointments", request, AppointmentHoldResponse.class);

		AvailabilitySlotResponse[] slotsAfter = restTemplate.getForObject(
				"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
				AvailabilitySlotResponse[].class,
				request.branchId(),
				LocalDate.now(AppTimeZone.ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
				request.serviceTypeId());
		assertThat(slotsAfter).extracting(AvailabilitySlotResponse::id).doesNotContain(request.slotId());

		// No Idempotency-Key here - a genuinely separate request for the exact same slot, not a
		// client retry of the first one. Each branch/timeslot has exactly one appointment's worth
		// of capacity (see TimeSlotGenerationService), so this is the ordinary "already taken"
		// conflict, same as a different customer racing for it - no email-specific check needed.
		ResponseEntity<ProblemDetail> secondAttempt = restTemplate.postForEntity("/api/v1/appointments", request, ProblemDetail.class);
		assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		// The stable code the frontend matches on, rather than the detail text.
		assertThat(secondAttempt.getBody().getProperties()).containsEntry("code", "SLOT_FULL");
	}

	@Test
	void createAppointment_replayedIdempotencyKeySameBody_returnsOriginalResponseNoNewHold() {
		NewClientBookingRequest request = newClientRequestForAvailableSlot();
		HttpHeaders headers = new HttpHeaders();
		headers.set("Idempotency-Key", "replay-key-" + UUID.randomUUID());
		HttpEntity<NewClientBookingRequest> entity = new HttpEntity<>(request, headers);

		AppointmentHoldResponse first =
				restTemplate.exchange("/api/v1/appointments", HttpMethod.POST, entity, AppointmentHoldResponse.class).getBody();
		AppointmentHoldResponse second =
				restTemplate.exchange("/api/v1/appointments", HttpMethod.POST, entity, AppointmentHoldResponse.class).getBody();

		assertThat(second.appointmentId()).isEqualTo(first.appointmentId());
		assertThat(second.referenceCode()).isEqualTo(first.referenceCode());
	}

	// The DTOs carry enums, but the wire format must stay the plain strings the frontend expects.
	@Test
	void wireFormat_sendsAndReturnsEnumsAsTheirPlainNames() {
		NewClientBookingRequest request = newClientRequestForAvailableSlot();
		String body = """
				{"clientType":"NEW_CLIENT","branchId":"%s","serviceTypeId":"%s","slotId":"%s","fullName":"Wire Format","email":"%s","phone":"+27825550198"}"""
				.formatted(request.branchId(), request.serviceTypeId(), request.slotId(), request.email());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<String> hold = restTemplate.postForEntity("/api/v1/appointments", new HttpEntity<>(body, headers), String.class);
		assertThat(hold.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(hold.getBody()).contains("\"status\":\"PENDING_CONFIRMATION\"");

		String actionLink = hold.getBody().replaceAll("(?s).*\"actionLink\":\"([^\"]+)\".*", "$1");
		ResponseEntity<String> confirmed =
				restTemplate.postForEntity("/api/v1/confirmations/{token}", null, String.class, extractToken(actionLink));
		assertThat(confirmed.getBody()).contains("\"status\":\"CONFIRMED\"");
	}

	private NewClientBookingRequest newClientRequestForAvailableSlot() {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		UUID branchId = branches[0].id();
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);
		UUID serviceTypeId = serviceTypes[0].id();
		UUID slotId = BookingFixtures.firstAvailableSlotId(restTemplate, clock, branchId, serviceTypeId);

		String email = "lindiwe.demo+" + UUID.randomUUID() + "@example.com";
		return new NewClientBookingRequest(ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Lindiwe Dube", email, "+27825550199");
	}

}
