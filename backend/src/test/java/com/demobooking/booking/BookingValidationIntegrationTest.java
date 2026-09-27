package com.demobooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import com.demobooking.booking.dto.NewClientBookingRequest;
import com.demobooking.branch.dto.AvailabilitySlotResponse;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import com.demobooking.common.AppTimeZone;
import com.demobooking.customer.ClientType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Server-side consistency checks on a booking request that bean validation can't express. */
@IntegrationTest
class BookingValidationIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void mismatchedBranchId_returns422() {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		UUID slotBranchId = branches[0].id();
		UUID otherBranchId = branches[1].id();
		UUID serviceTypeId = restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class)[0].id();
		LocalDate nextTuesday = LocalDate.now(AppTimeZone.ZONE).with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
		AvailabilitySlotResponse[] slots = restTemplate.getForObject(
				"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
				AvailabilitySlotResponse[].class,
				slotBranchId,
				nextTuesday,
				serviceTypeId);
		UUID slotId = slots[slots.length - 1].id();

		NewClientBookingRequest request = new NewClientBookingRequest(
				ClientType.NEW_CLIENT, otherBranchId, serviceTypeId, slotId, "Mismatch Test", "branch.mismatch@example.com", "+27825550300");
		ResponseEntity<ProblemDetail> response = restTemplate.postForEntity("/api/v1/appointments", request, ProblemDetail.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		// Rejected before reserving, so the slot is still offered.
		AvailabilitySlotResponse[] after = restTemplate.getForObject(
				"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
				AvailabilitySlotResponse[].class,
				slotBranchId,
				nextTuesday,
				serviceTypeId);
		assertThat(after).extracting(AvailabilitySlotResponse::id).contains(slotId);
	}

}
