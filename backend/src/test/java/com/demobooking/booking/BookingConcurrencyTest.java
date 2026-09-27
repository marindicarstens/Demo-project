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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The single most important test in this codebase. Fires
 * more concurrent booking requests at one slot than it has capacity for, and asserts exactly
 * `capacity` succeed. This is what actually protects the overbooking invariant; everything else
 * (the DB constraint, the optimistic lock, the retry loop in BookingService) exists to make this
 * test pass under real concurrent load, not just in a single-threaded happy path.
 */
@IntegrationTest
class BookingConcurrencyTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void concurrentBookingRequests_neverExceedSlotCapacity() throws InterruptedException {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);
		LocalDate nextMonday = LocalDate.now(AppTimeZone.ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

		UUID branchId = branches[0].id();
		UUID serviceTypeId = serviceTypes[0].id();
		AvailabilitySlotResponse[] slots = restTemplate.getForObject(
				"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
				AvailabilitySlotResponse[].class,
				branchId,
				nextMonday,
				serviceTypeId);
		AvailabilitySlotResponse targetSlot = slots[0];
		int capacity = targetSlot.remainingCapacity(); // 1, per TimeSlotGenerationService's DEFAULT_CAPACITY
		int concurrentRequests = capacity * 3; // deliberately more contenders than capacity

		ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
		try {
			List<Callable<HttpStatus>> attempts = IntStream.range(0, concurrentRequests)
					.<Callable<HttpStatus>>mapToObj(i -> () -> attemptBooking(branchId, serviceTypeId, targetSlot.id(), i))
					.toList();

			List<Future<HttpStatus>> futures = pool.invokeAll(attempts);
			List<HttpStatus> results = futures.stream().map(BookingFixtures::getUnchecked).collect(Collectors.toList());

			long succeeded = results.stream().filter(status -> status == HttpStatus.CREATED).count();
			long rejected = results.stream().filter(status -> status == HttpStatus.CONFLICT).count();

			assertThat(succeeded).as("exactly `capacity` bookings should succeed, never more").isEqualTo(capacity);
			assertThat(rejected).isEqualTo(concurrentRequests - capacity);
		} finally {
			pool.shutdown();
		}

		// Confirm the persisted state agrees: no slot ever left able to report negative or
		// over-capacity remaining availability.
		AvailabilitySlotResponse[] afterSlots = restTemplate.getForObject(
				"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
				AvailabilitySlotResponse[].class,
				branchId,
				nextMonday,
				serviceTypeId);
		boolean stillListed = Arrays.stream(afterSlots).anyMatch(s -> s.id().equals(targetSlot.id()));
		assertThat(stillListed).as("a fully-booked slot should no longer appear as available").isFalse();
	}

	private HttpStatus attemptBooking(UUID branchId, UUID serviceTypeId, UUID slotId, int index) {
		NewClientBookingRequest request = new NewClientBookingRequest(
				ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Concurrency Test " + index, "concurrency" + index + "@example.com", "+2782555" + (1000 + index));
		// String, not the DTO: only the status code matters, and a 409 body is a ProblemDetail.
		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/appointments", request, String.class);
		return (HttpStatus) response.getStatusCode();
	}

}
