package com.demobooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.demobooking.IntegrationTest;
import com.demobooking.MutableClock;
import com.demobooking.booking.dto.AppointmentHoldResponse;
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
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Calls ExpirySweepService.sweep() directly rather than waiting for its real @Scheduled
 * interval, and moves the MutableClock past the confirmation TTL instead of waiting it out, so
 * this test runs in milliseconds instead of a minute-plus.
 *
 * The NotificationSender spy (for the poisoned-item test) gives this class its own Spring
 * context; it still shares the one Postgres container, so every test picks free slots itself.
 */
@IntegrationTest
class ExpirySweepIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ExpirySweepService expirySweepService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private TimeSlotRepository timeSlotRepository;

	@MockitoSpyBean
	private NotificationSender notificationSender;

	// Unused here; declared so this class shares RescheduleConcurrencyTest's cached context.
	@MockitoSpyBean
	private AppointmentAuthorizer authorizer;

	@Autowired
	private MutableClock clock;

	// Past the test profile's 1-minute confirmation TTL.
	private static final Duration PAST_CONFIRMATION_TTL = Duration.ofMinutes(2);

	@Test
	void sweep_expiresLapsedHolds_releasesCapacity_andSendsExpiryNotice() {
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
		UUID slotId = slots[0].id();
		int remainingBeforeHold = slots[0].remainingCapacity();

		NewClientBookingRequest request =
				new NewClientBookingRequest(ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Expiry Test", "expiry.test@example.com", "+27825551234");
		AppointmentHoldResponse hold = restTemplate.postForObject("/api/v1/appointments", request, AppointmentHoldResponse.class);
		AtomicReference<SentEmail> expiryNotice = new AtomicReference<>();
		doAnswer(invocation -> {
			SentEmail sent = (SentEmail) invocation.callRealMethod();
			expiryNotice.set(sent);
			return sent;
		}).when(notificationSender).sendExpiryNotice(argThat(appointment -> appointment.getId().equals(hold.appointmentId())));

		clock.advance(PAST_CONFIRMATION_TTL);

		expirySweepService.sweep();

		Appointment expired = appointmentRepository.findById(hold.appointmentId()).orElseThrow();
		assertThat(expired.getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
		assertThat(timeSlotRepository.findById(slotId).orElseThrow().getRemainingCapacity()).isEqualTo(remainingBeforeHold);

		boolean hasExpiryNotice = notificationRepository.findAll().stream()
				.anyMatch(n -> n.getAppointment().getId().equals(hold.appointmentId()) && n.getType() == NotificationType.EXPIRY_NOTICE);
		assertThat(hasExpiryNotice).isTrue();
		assertThat(expiryNotice.get().subject()).isEqualTo("Your appointment could not be confirmed");
	}

	@Test
	void sweep_expiresLapsedRescheduleRequests_releasesTheHeldNewSlot_andLeavesTheAppointmentUntouched() {
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
		UUID originalSlotId = slots[0].id();
		UUID newSlotId = slots[1].id();
		int remainingAtNewSlotBeforeRequest = slots[1].remainingCapacity();

		NewClientBookingRequest bookingRequest =
				new NewClientBookingRequest(ClientType.NEW_CLIENT, branchId, serviceTypeId, originalSlotId, "Reschedule Expiry Test", "reschedule.expiry.test@example.com", "+27825551234");
		AppointmentHoldResponse hold = restTemplate.postForObject("/api/v1/appointments", bookingRequest, AppointmentHoldResponse.class);
		String rawConfirmToken = BookingFixtures.extractToken(hold.simulatedEmail().actionLink());
		ConfirmationResultResponse confirmed =
				restTemplate.postForObject("/api/v1/confirmations/{token}", null, ConfirmationResultResponse.class, rawConfirmToken);

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(confirmed.accessToken());
		restTemplate.postForObject(
				"/api/v1/appointments/{id}/reschedule-request",
				new HttpEntity<>(new RescheduleRequest(newSlotId), headers),
				RescheduleRequestResponse.class,
				confirmed.appointmentId());

		assertThat(timeSlotRepository.findById(newSlotId).orElseThrow().getRemainingCapacity()).isEqualTo(remainingAtNewSlotBeforeRequest - 1);
		AtomicReference<SentEmail> rescheduleExpiryNotice = new AtomicReference<>();
		doAnswer(invocation -> {
			SentEmail sent = (SentEmail) invocation.callRealMethod();
			rescheduleExpiryNotice.set(sent);
			return sent;
		}).when(notificationSender).sendRescheduleExpiryNotice(argThat(appointment -> appointment.getId().equals(confirmed.appointmentId())), any());

		clock.advance(PAST_CONFIRMATION_TTL);

		expirySweepService.sweep();

		assertThat(timeSlotRepository.findById(newSlotId).orElseThrow().getRemainingCapacity())
				.as("the held new slot should be released back to its pre-request capacity")
				.isEqualTo(remainingAtNewSlotBeforeRequest);

		// The whole point: unlike
		// a lapsed booking hold, this appointment was already CONFIRMED before the request and
		// must stay exactly that way, on its original slot - never EXPIRED.
		Appointment appointment = appointmentRepository.findById(confirmed.appointmentId()).orElseThrow();
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(appointment.getTimeSlot().getId()).isEqualTo(originalSlotId);

		AppointmentResponse lookedUp = restTemplate.getForObject(
				"/api/v1/appointments/lookup?reference={reference}&email={email}",
				AppointmentResponse.class,
				confirmed.referenceCode(),
				"reschedule.expiry.test@example.com");
		assertThat(lookedUp.status()).isEqualTo(AppointmentStatus.CONFIRMED);

		boolean hasRescheduleExpiryNotice = notificationRepository.findAll().stream()
				.anyMatch(n -> n.getAppointment().getId().equals(confirmed.appointmentId())
						&& n.getType() == NotificationType.EXPIRY_NOTICE
						&& n.getSimulatedPayload().contains("could not be confirmed in"));
		assertThat(hasRescheduleExpiryNotice).isTrue();
		assertThat(rescheduleExpiryNotice.get().subject()).isEqualTo("Your reschedule request expired");
	}

	@Test
	void lapsedReschedule_sweptTwice_releasesNewSlotOnlyOnce() {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);
		UUID branchId = branches[0].id();
		UUID serviceTypeId = serviceTypes[0].id();
		AvailabilitySlotResponse[] slots = availableNextMonday(branchId, serviceTypeId);
		UUID originalSlotId = slots[0].id();
		UUID newSlotId = slots[1].id();

		ConfirmationResultResponse a = bookAndConfirm(branchId, serviceTypeId, originalSlotId, "sweep.twice.a@example.com");
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(a.accessToken());
		restTemplate.postForObject(
				"/api/v1/appointments/{id}/reschedule-request",
				new HttpEntity<>(new RescheduleRequest(newSlotId), headers),
				RescheduleRequestResponse.class,
				a.appointmentId());

		Notification rescheduleRequest = rescheduleRequestNotificationFor(a.appointmentId());
		clock.advance(PAST_CONFIRMATION_TTL);

		expirySweepService.sweep();
		assertThat(timeSlotRepository.findById(newSlotId).orElseThrow().getBookedCount()).isZero();

		// The released slot is now legitimately someone else's. A second sweep must not see the
		// already-handled request again and release B's capacity out from under it.
		bookAndConfirm(branchId, serviceTypeId, newSlotId, "sweep.twice.b@example.com");
		expirySweepService.sweep();

		assertThat(timeSlotRepository.findById(newSlotId).orElseThrow().getBookedCount()).isEqualTo(1);
		assertThat(notificationRepository.findById(rescheduleRequest.getId()).orElseThrow().getTokenConsumedAt()).isNotNull();
		long expiryNoticesForA = notificationRepository.findAll().stream()
				.filter(n -> n.getAppointment().getId().equals(a.appointmentId()) && n.getType() == NotificationType.EXPIRY_NOTICE)
				.count();
		assertThat(expiryNoticesForA).isEqualTo(1);
	}

	@Test
	void oneFailingItem_doesNotBlockOthers() {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		ServiceTypeResponse[] serviceTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class);
		UUID branchId = branches[0].id();
		UUID serviceTypeId = serviceTypes[0].id();
		AvailabilitySlotResponse[] slots = availableNextMonday(branchId, serviceTypeId);

		AppointmentHoldResponse a = hold(branchId, serviceTypeId, slots[0].id(), "poison.a@example.com");
		AppointmentHoldResponse b = hold(branchId, serviceTypeId, slots[1].id(), "poison.b@example.com");
		AppointmentHoldResponse c = hold(branchId, serviceTypeId, slots[2].id(), "poison.c@example.com");
		clock.advance(PAST_CONFIRMATION_TTL);
		UUID bId = b.appointmentId();
		doThrow(new RuntimeException("simulated poisoned row"))
				.when(notificationSender)
				.sendExpiryNotice(argThat(appointment -> appointment.getId().equals(bId)));

		assertThatCode(expirySweepService::sweep).doesNotThrowAnyException();

		assertThat(appointmentRepository.findById(a.appointmentId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
		assertThat(appointmentRepository.findById(c.appointmentId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
		assertThat(timeSlotRepository.findById(slots[0].id()).orElseThrow().getBookedCount()).isZero();
		assertThat(timeSlotRepository.findById(slots[2].id()).orElseThrow().getBookedCount()).isZero();

		// B's transaction rolled back as a whole: still held, still pending, retried next run.
		assertThat(appointmentRepository.findById(bId).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING_CONFIRMATION);
		assertThat(timeSlotRepository.findById(slots[1].id()).orElseThrow().getBookedCount()).isEqualTo(1);
	}

	private AvailabilitySlotResponse[] availableNextMonday(UUID branchId, UUID serviceTypeId) {
		return BookingFixtures.availability(restTemplate, branchId, serviceTypeId, BookingFixtures.nextMonday(clock));
	}

	private ConfirmationResultResponse bookAndConfirm(UUID branchId, UUID serviceTypeId, UUID slotId, String email) {
		NewClientBookingRequest request = new NewClientBookingRequest(ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Sweep Test", email, "+27825551234");
		return BookingFixtures.bookAndConfirm(restTemplate, request);
	}

	private AppointmentHoldResponse hold(UUID branchId, UUID serviceTypeId, UUID slotId, String email) {
		NewClientBookingRequest request = new NewClientBookingRequest(ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Sweep Test", email, "+27825551234");
		return restTemplate.postForObject("/api/v1/appointments", request, AppointmentHoldResponse.class);
	}

	private Notification rescheduleRequestNotificationFor(UUID appointmentId) {
		List<Notification> all = notificationRepository.findAll();
		return all.stream()
				.filter(n -> n.getAppointment().getId().equals(appointmentId) && n.getType() == NotificationType.RESCHEDULE_REQUEST)
				.findFirst()
				.orElseThrow();
	}

}
