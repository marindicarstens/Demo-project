package com.demobooking.booking;

import com.demobooking.booking.dto.AppointmentHoldResponse;
import com.demobooking.booking.dto.ConfirmationResultResponse;
import com.demobooking.booking.dto.NewClientBookingRequest;
import com.demobooking.branch.dto.AvailabilitySlotResponse;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Future;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

/** HTTP-level helpers the booking integration tests share, so each test file doesn't carry its own copy. */
final class BookingFixtures {

	private BookingFixtures() {
	}

	/** The raw token is the last path segment of an emailed action link - the only place a caller ever sees it. */
	static String extractToken(String actionLink) {
		return actionLink.substring(actionLink.lastIndexOf('/') + 1);
	}

	static <T> T getUnchecked(Future<T> future) {
		try {
			return future.get();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Always a weekday with a full day of generated slots, whatever day the suite runs on. Takes
	 * the app's clock, so "today" here is the same day the server sees. */
	static LocalDate nextMonday(Clock clock) {
		return LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
	}

	static AvailabilitySlotResponse[] availability(TestRestTemplate restTemplate, UUID branchId, UUID serviceTypeId, LocalDate date) {
		return restTemplate.getForObject(
				"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
				AvailabilitySlotResponse[].class,
				branchId,
				date,
				serviceTypeId);
	}

	static UUID firstAvailableSlotId(TestRestTemplate restTemplate, Clock clock, UUID branchId, UUID serviceTypeId) {
		return availability(restTemplate, branchId, serviceTypeId, nextMonday(clock))[0].id();
	}

	/** 0 when the slot isn't listed: availability leaves fully-booked slots out entirely. */
	static int remainingCapacityOf(Collection<AvailabilitySlotResponse> listing, UUID slotId) {
		return listing.stream()
				.filter(s -> s.id().equals(slotId))
				.findFirst()
				.map(AvailabilitySlotResponse::remainingCapacity)
				.orElse(0);
	}

	static int remainingCapacityOf(AvailabilitySlotResponse[] listing, UUID slotId) {
		return remainingCapacityOf(Arrays.asList(listing), slotId);
	}

	/** Reads a body only on 2xx - an error ProblemDetail's numeric "status" doesn't fit a DTO's enum status. */
	static <T> ResponseEntity<T> successBody(ResponseEntity<String> raw, ObjectMapper objectMapper, Class<T> type) {
		T body = raw.getStatusCode().is2xxSuccessful() ? objectMapper.readValue(raw.getBody(), type) : null;
		return ResponseEntity.status(raw.getStatusCode()).body(body);
	}

	/** Creates a hold and clicks its confirm link, exactly as the customer would. */
	static ConfirmationResultResponse bookAndConfirm(TestRestTemplate restTemplate, NewClientBookingRequest request) {
		AppointmentHoldResponse hold = restTemplate.postForObject("/api/v1/appointments", request, AppointmentHoldResponse.class);
		String rawToken = extractToken(hold.simulatedEmail().actionLink());
		return restTemplate.postForObject("/api/v1/confirmations/{token}", null, ConfirmationResultResponse.class, rawToken);
	}

}
