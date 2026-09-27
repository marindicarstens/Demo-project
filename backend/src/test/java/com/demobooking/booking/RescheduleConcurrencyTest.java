package com.demobooking.booking;

import static com.demobooking.booking.BookingFixtures.extractToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.demobooking.IntegrationTest;
import com.demobooking.MutableClock;
import com.demobooking.booking.dto.AppointmentResponse;
import com.demobooking.booking.dto.ConfirmationResultResponse;
import com.demobooking.booking.dto.NewClientBookingRequest;
import com.demobooking.booking.dto.RescheduleRequest;
import com.demobooking.booking.dto.RescheduleRequestResponse;
import com.demobooking.branch.TimeSlotRepository;
import com.demobooking.branch.dto.AvailabilitySlotResponse;
import com.demobooking.branch.dto.BranchResponse;
import com.demobooking.branch.dto.ServiceTypeResponse;
import com.demobooking.common.AppTimeZone;
import com.demobooking.customer.ClientType;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

/**
 * A reschedule *request* racing fresh bookings for the same target slot must fully succeed or
 * fully fail, never partially. Capacity is claimed at request time (RescheduleService
 * .requestReschedule, via the same OptimisticRetry as BookingService.createHold), so that is
 * where the invariant has to hold.
 * The losing reschedule-request attempt must leave the original appointment exactly as it was:
 * still CONFIRMED, still at its original slot, with that slot's capacity untouched. Mirrors
 * BookingConcurrencyTest's structure for the equivalent booking-side guarantee.
 */
@IntegrationTest
class RescheduleConcurrencyTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ExpirySweepService expirySweepService;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private TimeSlotRepository timeSlotRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// Same overrides as ExpirySweepIntegrationTest, so both classes share one cached context.
	@MockitoSpyBean
	private NotificationSender notificationSender;

	@MockitoSpyBean
	private AppointmentAuthorizer authorizer;

	@Autowired
	private MutableClock clock;

	@Test
	void aRescheduleRequestRacingFreshBookings_forOneRemainingSpot_resolvesCleanlyOneWayOrTheOther() throws InterruptedException {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);
		// A different date from every other test class in this suite (which all use "next
		// Monday") - this test's cancellation traffic (via the losing reschedule branch) means a
		// shared date's slots can be left partially released rather than only ever monotonically
		// filling, so this class needs dates none of the others ever touch.
		LocalDate targetDate = LocalDate.now(AppTimeZone.ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(3);
		UUID branchId = branches[0].id();
		UUID serviceTypeId = serviceTypes[0].id();

		AvailabilitySlotResponse[] slots = availability(branchId, serviceTypeId, targetDate);
		UUID targetSlotId = slots[0].id();
		int capacity = slots[0].remainingCapacity(); // 1, per TimeSlotGenerationService's DEFAULT_CAPACITY
		UUID originalSlotId = slots[1].id(); // the appointment-to-be-rescheduled's starting slot

		// Fill the target down to exactly one remaining spot, so the race below is for that one spot.
		for (int i = 0; i < capacity - 1; i++) {
			bookAndConfirm(branchId, serviceTypeId, targetSlotId, "target.filler" + i + "@example.com");
		}
		assertThat(remainingCapacityOf(targetSlotId, branchId, serviceTypeId, targetDate)).isEqualTo(1);

		ConfirmedBooking toReschedule = bookAndConfirm(branchId, serviceTypeId, originalSlotId, "reschedule.racer@example.com");

		int contenders = 3; // more than the one remaining spot
		ExecutorService pool = Executors.newFixedThreadPool(contenders);
		List<HttpStatus> results;
		try {
			List<Callable<HttpStatus>> attempts = List.of(
					() -> attemptRescheduleRequest(toReschedule, targetSlotId),
					() -> attemptFreshBooking(branchId, serviceTypeId, targetSlotId, "target.racer1@example.com"),
					() -> attemptFreshBooking(branchId, serviceTypeId, targetSlotId, "target.racer2@example.com"));

			List<Future<HttpStatus>> futures = pool.invokeAll(attempts);
			results = futures.stream().map(BookingFixtures::getUnchecked).collect(Collectors.toList());

			long succeeded = results.stream().filter(s -> s == HttpStatus.OK || s == HttpStatus.CREATED).count();
			assertThat(succeeded).as("exactly the one remaining spot should be claimed, by exactly one contender").isEqualTo(1);
		} finally {
			pool.shutdown();
		}

		// The one remaining spot must always be claimed by someone - never left un-granted, and
		// never over-granted despite 3 contenders racing for it. This holds immediately after the
		// request-phase race, before any confirm click - the new capacity-reservation point.
		assertThat(remainingCapacityOf(targetSlotId, branchId, serviceTypeId, targetDate)).isEqualTo(0);

		boolean rescheduleRequestWon = results.get(0) == HttpStatus.OK;
		AppointmentResponse appointment;
		if (rescheduleRequestWon) {
			// Winning the request only reserves the new slot - the appointment itself hasn't moved
			// yet, and only does once the (separately-tested) confirm step is driven through the
			// emailed link. Drive it here too, so this test still proves the whole flow ends up
			// fully-moved, not just capacity-reserved.
			appointment = restTemplate.postForObject("/api/v1/reschedule-confirmations/{token}", null, AppointmentResponse.class, wonRescheduleConfirmToken.get());
			assertThat(appointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);
			assertThat(appointment.startTime()).isEqualTo(slots[0].startTime());
			assertThat(remainingCapacityOf(originalSlotId, branchId, serviceTypeId, targetDate))
					.as("the old slot's capacity should be released once the reschedule is confirmed")
					.isEqualTo(capacity);
		} else {
			// A fresh booking won the race instead: the reschedule request must have failed
			// entirely (409, no partial reservation), leaving the original appointment completely
			// untouched - still on its original slot, that slot's capacity exactly as before.
			assertThat(results.get(0)).isEqualTo(HttpStatus.CONFLICT);
			appointment = restTemplate.getForObject(
					"/api/v1/appointments/lookup?reference={reference}&email={email}",
					AppointmentResponse.class,
					toReschedule.referenceCode,
					"reschedule.racer@example.com");
			assertThat(appointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);
			assertThat(appointment.startTime()).isEqualTo(slots[1].startTime());
			assertThat(remainingCapacityOf(originalSlotId, branchId, serviceTypeId, targetDate)).isEqualTo(capacity - 1);
		}
	}

	/**
	 * confirmReschedule and the reschedule-expiry sweep must never both commit: that would leave
	 * the appointment on the new slot with the new slot's capacity released. They write no
	 * common row except the RESCHEDULE_REQUEST notification, so Notification's @Version is what
	 * makes one of them lose.
	 *
	 * Deterministic interleave: the sweep's item transaction reads the request and then parks
	 * (inside sendRescheduleExpiryNotice, before anything is flushed); the confirm runs and
	 * commits in full; then the sweep resumes and tries to commit on its now-stale read.
	 */
	@Test
	void confirmReschedule_vsSweep_exactlyOneWins() throws Exception {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);
		LocalDate targetDate = LocalDate.now(AppTimeZone.ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(4);
		UUID branchId = branches[0].id();
		UUID serviceTypeId = serviceTypes[0].id();
		AvailabilitySlotResponse[] slots = availability(branchId, serviceTypeId, targetDate);
		UUID originalSlotId = slots[0].id();
		UUID newSlotId = slots[1].id();

		ConfirmedBooking booking = bookAndConfirm(branchId, serviceTypeId, originalSlotId, "reschedule.vs.sweep@example.com");
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);
		RescheduleRequestResponse requested = restTemplate.postForObject(
				"/api/v1/appointments/{id}/reschedule-request",
				new HttpEntity<>(new RescheduleRequest(newSlotId), headers),
				RescheduleRequestResponse.class,
				booking.appointmentId);
		String rawRescheduleToken = extractToken(requested.simulatedEmail().actionLink());

		Notification rescheduleRequest = notificationRepository.findAll().stream()
				.filter(n -> n.getAppointment().getId().equals(booking.appointmentId) && n.getType() == NotificationType.RESCHEDULE_REQUEST)
				.findFirst()
				.orElseThrow();
		// Past the test profile's 1-minute confirmation TTL.
		clock.advance(Duration.ofMinutes(2));

		CountDownLatch sweepHoldsItsRead = new CountDownLatch(1);
		CountDownLatch confirmCommitted = new CountDownLatch(1);
		doAnswer(invocation -> {
			sweepHoldsItsRead.countDown();
			confirmCommitted.await(30, TimeUnit.SECONDS);
			return invocation.callRealMethod();
		}).when(notificationSender).sendRescheduleExpiryNotice(argThat(a -> a.getId().equals(booking.appointmentId)), any());

		ExecutorService sweepThread = Executors.newSingleThreadExecutor();
		try {
			Future<?> sweep = sweepThread.submit(() -> expirySweepService.sweepOneExpiredRescheduleRequest(rescheduleRequest.getId()));
			assertThat(sweepHoldsItsRead.await(30, TimeUnit.SECONDS)).isTrue();

			// The sweep saw the link as lapsed; the confirm click saw it a moment earlier as still
			// valid. Reproduce that by un-expiring it with a plain SQL write, which leaves the
			// version the sweep already read untouched.
			jdbcTemplate.update(
					"UPDATE notification SET token_expires_at = ? WHERE id = ?",
					Timestamp.from(clock.instant().plusSeconds(60)),
					rescheduleRequest.getId());
			restTemplate.postForEntity("/api/v1/reschedule-confirmations/{token}", null, AppointmentResponse.class, rawRescheduleToken);
			confirmCommitted.countDown();

			sweep.get(30, TimeUnit.SECONDS);
		} finally {
			confirmCommitted.countDown();
			sweepThread.shutdown();
		}

		UUID appointmentSlotId = appointmentRepository.findById(booking.appointmentId).orElseThrow().getTimeSlot().getId();
		int newSlotBooked = timeSlotRepository.findById(newSlotId).orElseThrow().getBookedCount();
		if (appointmentSlotId.equals(newSlotId)) {
			assertThat(newSlotBooked).as("the appointment moved to the new slot, so that slot must still be held").isEqualTo(1);
		} else {
			assertThat(appointmentSlotId).isEqualTo(originalSlotId);
			assertThat(newSlotBooked).as("the appointment stayed put, so the new slot must be released").isZero();
		}
	}

	/**
	 * A website cancel that read the appointment before a reschedule confirm committed must not
	 * release the old slot a second time: its stale write loses the optimistic-lock race, and the
	 * retry re-reads the appointment on its new slot and releases that one instead.
	 *
	 * Deterministic interleave: the cancel's first attempt loads the appointment and then parks
	 * (inside AppointmentAuthorizer.loadAuthorized); the reschedule confirm runs and commits in
	 * full; then the cancel resumes on its now-stale read.
	 */
	@Test
	void websiteCancel_vsRescheduleConfirm_retriesAndReleasesTheNewSlot() throws Exception {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);
		LocalDate targetDate = LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(4);
		UUID branchId = branches[0].id();
		UUID serviceTypeId = serviceTypes[0].id();
		AvailabilitySlotResponse[] slots = availability(branchId, serviceTypeId, targetDate);
		UUID originalSlotId = slots[0].id();
		UUID newSlotId = slots[1].id();

		ConfirmedBooking booking = bookAndConfirm(branchId, serviceTypeId, originalSlotId, "cancel.vs.reschedule@example.com");
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);
		RescheduleRequestResponse requested = restTemplate.postForObject(
				"/api/v1/appointments/{id}/reschedule-request",
				new HttpEntity<>(new RescheduleRequest(newSlotId), headers),
				RescheduleRequestResponse.class,
				booking.appointmentId);
		String rawRescheduleToken = extractToken(requested.simulatedEmail().actionLink());

		CountDownLatch cancelHoldsItsRead = new CountDownLatch(1);
		CountDownLatch confirmCommitted = new CountDownLatch(1);
		AtomicInteger cancelAttempts = new AtomicInteger();
		doAnswer(invocation -> {
			Object loaded = invocation.callRealMethod();
			if (cancelAttempts.incrementAndGet() == 1) {
				cancelHoldsItsRead.countDown();
				confirmCommitted.await(30, TimeUnit.SECONDS);
			}
			return loaded;
		}).when(authorizer).loadAuthorized(eq(booking.appointmentId), any(), any(), any());

		ExecutorService cancelThread = Executors.newSingleThreadExecutor();
		ResponseEntity<Void> cancelled;
		try {
			Future<ResponseEntity<Void>> cancel = cancelThread.submit(() -> restTemplate.exchange(
					"/api/v1/appointments/{id}", HttpMethod.DELETE, new HttpEntity<>(headers), Void.class, booking.appointmentId));
			assertThat(cancelHoldsItsRead.await(30, TimeUnit.SECONDS)).isTrue();

			ResponseEntity<AppointmentResponse> confirmed =
					restTemplate.postForEntity("/api/v1/reschedule-confirmations/{token}", null, AppointmentResponse.class, rawRescheduleToken);
			assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
			confirmCommitted.countDown();

			cancelled = cancel.get(30, TimeUnit.SECONDS);
		} finally {
			confirmCommitted.countDown();
			cancelThread.shutdown();
		}

		assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		assertThat(cancelAttempts).as("the stale first attempt must lose and be retried").hasValue(2);
		Appointment appointment = appointmentRepository.findById(booking.appointmentId).orElseThrow();
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(appointment.getTimeSlot().getId()).isEqualTo(newSlotId);
		assertThat(timeSlotRepository.findById(newSlotId).orElseThrow().getBookedCount())
				.as("the cancel must release the slot the appointment was moved to")
				.isZero();
		assertThat(timeSlotRepository.findById(originalSlotId).orElseThrow().getBookedCount())
				.as("the old slot was released once, by the reschedule confirm")
				.isZero();
	}

	private record ConfirmedBooking(UUID appointmentId, String referenceCode, String accessToken) {
	}

	private ConfirmedBooking bookAndConfirm(UUID branchId, UUID serviceTypeId, UUID slotId, String email) {
		NewClientBookingRequest request = new NewClientBookingRequest(ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Reschedule Race", email, "+27825550000");
		ConfirmationResultResponse confirmed = BookingFixtures.bookAndConfirm(restTemplate, request);
		return new ConfirmedBooking(confirmed.appointmentId(), confirmed.referenceCode(), confirmed.accessToken());
	}

	// Set only if this attempt wins the race - the winning thread stashes the raw confirm token
	// here so the test can drive the confirm step afterwards, outside the concurrent section.
	private final AtomicReference<String> wonRescheduleConfirmToken = new AtomicReference<>();

	private HttpStatus attemptRescheduleRequest(ConfirmedBooking booking, UUID newSlotId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);
		ResponseEntity<RescheduleRequestResponse> response = BookingFixtures.successBody(
				restTemplate.exchange(
						"/api/v1/appointments/{id}/reschedule-request",
						HttpMethod.POST,
						new HttpEntity<>(new RescheduleRequest(newSlotId), headers),
						String.class,
						booking.appointmentId),
				objectMapper,
				RescheduleRequestResponse.class);
		if (response.getStatusCode() == HttpStatus.OK) {
			wonRescheduleConfirmToken.set(extractToken(response.getBody().simulatedEmail().actionLink()));
		}
		return (HttpStatus) response.getStatusCode();
	}

	private HttpStatus attemptFreshBooking(UUID branchId, UUID serviceTypeId, UUID slotId, String email) {
		NewClientBookingRequest request = new NewClientBookingRequest(ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Fresh Racer", email, "+27825550001");
		ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/appointments", request, String.class);
		return (HttpStatus) response.getStatusCode();
	}

	private int remainingCapacityOf(UUID slotId, UUID branchId, UUID serviceTypeId, LocalDate date) {
		return BookingFixtures.remainingCapacityOf(availability(branchId, serviceTypeId, date), slotId);
	}

	private AvailabilitySlotResponse[] availability(UUID branchId, UUID serviceTypeId, LocalDate date) {
		return BookingFixtures.availability(restTemplate, branchId, serviceTypeId, date);
	}

}
