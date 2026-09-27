package com.demobooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import com.demobooking.booking.dto.AppointmentHoldResponse;
import com.demobooking.booking.dto.NewClientBookingRequest;
import com.demobooking.branch.dto.AvailabilitySlotResponse;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import com.demobooking.common.AppTimeZone;
import com.demobooking.customer.ClientType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates IdempotencyService's atomic SET-NX claim (see its Javadoc-equivalent comment): several
 * requests sharing one Idempotency-Key and body, fired concurrently at a slot with plenty of spare
 * capacity (so a capacity-based 409 can never masquerade as idempotency working), must resolve to
 * exactly one persisted appointment - never one-hold-per-request, and never a request silently
 * dropped instead of getting back that one shared result. Mirrors BookingConcurrencyTest's structure
 * for the equivalent capacity-side guarantee.
 */
@IntegrationTest
class DuplicateIdempotencyKeyConcurrencyTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void concurrentRequestsWithTheSameIdempotencyKeyAndBody_resolveToExactlyOneAppointment() throws InterruptedException {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);
		// A date none of the other test classes touch (same reasoning as RescheduleConcurrencyTest)
		// - this test asserts exactly one row exists for its email, so it can't share a slot whose
		// capacity or bookings some other test in the suite might also be touching.
		LocalDate targetDate = LocalDate.now(AppTimeZone.ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(4);
		UUID branchId = branches[0].id();
		UUID serviceTypeId = serviceTypes[0].id();
		AvailabilitySlotResponse[] slots = restTemplate.getForObject(
				"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
				AvailabilitySlotResponse[].class,
				branchId,
				targetDate,
				serviceTypeId);
		UUID slotId = slots[0].id();
		int capacity = slots[0].remainingCapacity(); // comfortably >1 contender, so this can never look like a capacity conflict instead

		String email = "idempotency.race@example.com";
		NewClientBookingRequest request = new NewClientBookingRequest(ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Idempotency Race", email, "+27825551111");
		String idempotencyKey = UUID.randomUUID().toString();

		int contenders = Math.min(5, capacity);
		ExecutorService pool = Executors.newFixedThreadPool(contenders);
		List<ResponseEntity<AppointmentHoldResponse>> results;
		try {
			List<Callable<ResponseEntity<AppointmentHoldResponse>>> attempts = IntStream.range(0, contenders)
					.<Callable<ResponseEntity<AppointmentHoldResponse>>>mapToObj(i -> () -> attempt(request, idempotencyKey))
					.toList();
			List<Future<ResponseEntity<AppointmentHoldResponse>>> futures = pool.invokeAll(attempts);
			results = futures.stream().map(BookingFixtures::getUnchecked).collect(Collectors.toList());
		} finally {
			pool.shutdown();
		}

		// Every response is either the replayed hold (201, same body every time) or a "still being
		// processed" conflict (409) - the IN_PROGRESS marker's own window - never anything else, and
		// never a second, distinct hold.
		assertThat(results).allSatisfy(r -> assertThat(r.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.CONFLICT));

		List<ResponseEntity<AppointmentHoldResponse>> successes =
				results.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).collect(Collectors.toList());
		assertThat(successes).as("at least one request must actually succeed").isNotEmpty();

		Set<UUID> distinctAppointmentIds = successes.stream().map(r -> r.getBody().appointmentId()).collect(Collectors.toSet());
		assertThat(distinctAppointmentIds)
				.as("every successful response must be the SAME replayed appointment, never a distinct second hold")
				.hasSize(1);

		// SQL, not findAll(): customer is a lazy association and this runs outside a transaction.
		Long actuallyPersisted = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM appointment a JOIN customer c ON c.id = a.customer_id WHERE lower(c.email) = lower(?)", Long.class, email);
		assertThat(actuallyPersisted).as("no duplicate row should ever have been persisted, even transiently").isEqualTo(1);
	}

	private ResponseEntity<AppointmentHoldResponse> attempt(NewClientBookingRequest request, String idempotencyKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Idempotency-Key", idempotencyKey);
		return BookingFixtures.successBody(
				restTemplate.exchange("/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(request, headers), String.class),
				objectMapper,
				AppointmentHoldResponse.class);
	}
}
