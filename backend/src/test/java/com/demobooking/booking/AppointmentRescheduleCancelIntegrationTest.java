package com.demobooking.booking;

import static com.demobooking.booking.BookingFixtures.extractToken;
import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import com.demobooking.MutableClock;
import com.demobooking.booking.dto.AppointmentResponse;
import com.demobooking.booking.dto.CancellationPreviewResponse;
import com.demobooking.booking.dto.CancellationResultResponse;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Looking up, rescheduling, and cancelling a confirmed appointment - see
 * docs/USER-GUIDE.md §5-6, including the "bare GET never cancels" guarantee. The reschedule
 * concurrency variant lives separately, in RescheduleConcurrencyTest - see its own Javadoc.
 */
@IntegrationTest
class AppointmentRescheduleCancelIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private TimeSlotRepository timeSlotRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private MutableClock clock;

	@Test
	void lookup_matchingReferenceAndEmail_returnsTheAppointment() {
		ConfirmedBooking booking = bookAndConfirm("lookup.test@example.com");

		ResponseEntity<AppointmentResponse> response = restTemplate.getForEntity(
				"/api/v1/appointments/lookup?reference={reference}&email={email}", AppointmentResponse.class, booking.referenceCode, "lookup.test@example.com");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().id()).isEqualTo(booking.appointmentId);
		assertThat(response.getBody().status()).isEqualTo(AppointmentStatus.CONFIRMED);
	}

	@Test
	void lookup_wrongEmail_returns404() {
		ConfirmedBooking booking = bookAndConfirm("lookup.wrong@example.com");

		ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
				"/api/v1/appointments/lookup?reference={reference}&email={email}", ProblemDetail.class, booking.referenceCode, "someone.else@example.com");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void requestReschedule_withBearerToken_reservesTheNewSlotButLeavesTheAppointmentOnItsOriginalSlotUntilConfirmed() {
		ConfirmedBooking booking = bookAndConfirm("reschedule.bearer@example.com");
		UUID newSlotId = anotherAvailableSlot(booking.branchId, booking.serviceTypeId, booking.slotId);

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);
		ResponseEntity<RescheduleRequestResponse> response = restTemplate.exchange(
				"/api/v1/appointments/{id}/reschedule-request",
				HttpMethod.POST,
				new HttpEntity<>(new RescheduleRequest(newSlotId), headers),
				RescheduleRequestResponse.class,
				booking.appointmentId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		// The point of this whole flow - nothing about the appointment or either slot's capacity
		// has changed yet; only the simulated email with the confirm link has been sent.
		assertThat(response.getBody().status()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(response.getBody().simulatedEmail().actionLink()).contains("/reschedule-confirm/");

		AppointmentResponse stillOriginal = restTemplate.getForObject(
				"/api/v1/appointments/lookup?reference={reference}&email={email}", AppointmentResponse.class, booking.referenceCode, "reschedule.bearer@example.com");
		assertThat(stillOriginal.startTime()).isEqualTo(booking.slotStartTime);

		// Capacity is 1 per slot (see TimeSlotGenerationService), so "held" means the slot no
		// longer appears in availability at all, not "remaining capacity dropped to 0" - a full
		// slot is excluded outright, per the same rule that made it disappear for a customer
		// re-browsing after booking it themselves.
		assertThat(remainingCapacityOf(newSlotId, booking.branchId, booking.serviceTypeId))
				.as("the requested new slot should already be held, even before confirmation")
				.isZero();
	}

	@Test
	void confirmReschedule_movesTheAppointmentAndReleasesTheOldSlot() {
		ConfirmedBooking booking = bookAndConfirm("reschedule.confirm@example.com");
		UUID newSlotId = anotherAvailableSlot(booking.branchId, booking.serviceTypeId, booking.slotId);
		String rawToken = requestRescheduleAndExtractToken(booking, newSlotId);

		ResponseEntity<AppointmentResponse> response =
				restTemplate.postForEntity("/api/v1/reschedule-confirmations/{token}", null, AppointmentResponse.class, rawToken);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().status()).isEqualTo(AppointmentStatus.CONFIRMED);

		int remainingAtOldSlot = remainingCapacityOf(booking.slotId, booking.branchId, booking.serviceTypeId);
		assertThat(remainingAtOldSlot).as("the old slot's capacity should be released").isGreaterThan(0);
	}

	@Test
	void confirmReschedule_unknownToken_returns404() {
		ResponseEntity<ProblemDetail> response =
				restTemplate.postForEntity("/api/v1/reschedule-confirmations/{token}", null, ProblemDetail.class, "not-a-real-token");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void confirmReschedule_expiredToken_returns410_andLeavesTheAppointmentOnItsOriginalSlot() {
		ConfirmedBooking booking = bookAndConfirm("reschedule.expired@example.com");
		UUID newSlotId = anotherAvailableSlot(booking.branchId, booking.serviceTypeId, booking.slotId);
		String rawToken = requestRescheduleAndExtractToken(booking, newSlotId);

		// Past the test profile's 1-minute confirmation TTL.
		clock.advance(Duration.ofMinutes(2));

		ResponseEntity<ProblemDetail> response =
				restTemplate.postForEntity("/api/v1/reschedule-confirmations/{token}", null, ProblemDetail.class, rawToken);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);

		AppointmentResponse stillOriginal = restTemplate.getForObject(
				"/api/v1/appointments/lookup?reference={reference}&email={email}", AppointmentResponse.class, booking.referenceCode, "reschedule.expired@example.com");
		assertThat(stillOriginal.startTime()).isEqualTo(booking.slotStartTime);
	}

	@Test
	void requestReschedule_withReferenceEmailAuth_alsoWorks() {
		ConfirmedBooking booking = bookAndConfirm("reschedule.refemail@example.com");
		UUID newSlotId = anotherAvailableSlot(booking.branchId, booking.serviceTypeId, booking.slotId);

		ResponseEntity<RescheduleRequestResponse> response = restTemplate.exchange(
				"/api/v1/appointments/{id}/reschedule-request?reference={reference}&email={email}",
				HttpMethod.POST,
				new HttpEntity<>(new RescheduleRequest(newSlotId)),
				RescheduleRequestResponse.class,
				booking.appointmentId,
				booking.referenceCode,
				"reschedule.refemail@example.com");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void requestReschedule_wrongCredentials_returns404_notLeakingWhetherTheAppointmentExists() {
		ConfirmedBooking booking = bookAndConfirm("reschedule.wrongcreds@example.com");
		UUID newSlotId = anotherAvailableSlot(booking.branchId, booking.serviceTypeId, booking.slotId);

		ResponseEntity<ProblemDetail> response = restTemplate.exchange(
				"/api/v1/appointments/{id}/reschedule-request?reference={reference}&email={email}",
				HttpMethod.POST,
				new HttpEntity<>(new RescheduleRequest(newSlotId)),
				ProblemDetail.class,
				booking.appointmentId,
				booking.referenceCode,
				"not-the-booking-email@example.com");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void requestReschedule_targetSlotServiceTypeNotApplicableToOriginalFlow_returns422() {
		ConfirmedBooking booking = bookAndConfirm("reschedule.wrongflow@example.com");

		ServiceTypeResponse[] existingClientOnlyTypes =
				restTemplate.getForObject("/api/v1/service-types?clientType=EXISTING_CLIENT", ServiceTypeResponse[].class);
		UUID existingClientOnlyServiceTypeId = Arrays.stream(existingClientOnlyTypes)
				.filter(t -> t.applicableClientType().name().equals("EXISTING_CLIENT"))
				.findFirst()
				.orElseThrow()
				.id();
		UUID incompatibleSlotId = firstAvailableSlotId(booking.branchId, existingClientOnlyServiceTypeId);

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);
		ResponseEntity<ProblemDetail> response = restTemplate.exchange(
				"/api/v1/appointments/{id}/reschedule-request",
				HttpMethod.POST,
				new HttpEntity<>(new RescheduleRequest(incompatibleSlotId), headers),
				ProblemDetail.class,
				booking.appointmentId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
	}

	@Test
	void requestReschedule_currentAppointmentTooSoon_returns422() {
		ConfirmedBooking booking = bookAndConfirm("reschedule.toosoon@example.com");
		backdateSlotToStartWithinTheNoticeWindow(booking.slotId);
		UUID newSlotId = anotherAvailableSlot(booking.branchId, booking.serviceTypeId, booking.slotId);

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);
		ResponseEntity<ProblemDetail> response = restTemplate.exchange(
				"/api/v1/appointments/{id}/reschedule-request",
				HttpMethod.POST,
				new HttpEntity<>(new RescheduleRequest(newSlotId), headers),
				ProblemDetail.class,
				booking.appointmentId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
	}

	@Test
	void requestReschedule_aSecondRequestWhileOnePending_returns409() {
		ConfirmedBooking booking = bookAndConfirm("reschedule.doublerequest@example.com");
		UUID firstNewSlotId = anotherAvailableSlot(booking.branchId, booking.serviceTypeId, booking.slotId);

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);
		ResponseEntity<RescheduleRequestResponse> first = restTemplate.exchange(
				"/api/v1/appointments/{id}/reschedule-request",
				HttpMethod.POST,
				new HttpEntity<>(new RescheduleRequest(firstNewSlotId), headers),
				RescheduleRequestResponse.class,
				booking.appointmentId);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

		UUID secondNewSlotId = availabilityFor(booking.branchId, booking.serviceTypeId).stream()
				.map(AvailabilitySlotResponse::id)
				.filter(id -> !id.equals(booking.slotId) && !id.equals(firstNewSlotId))
				.findFirst()
				.orElseThrow();
		ResponseEntity<ProblemDetail> second = restTemplate.exchange(
				"/api/v1/appointments/{id}/reschedule-request",
				HttpMethod.POST,
				new HttpEntity<>(new RescheduleRequest(secondNewSlotId), headers),
				ProblemDetail.class,
				booking.appointmentId);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void cancelFromWebsite_withBearerToken_releasesTheSlotAndIsIdempotentOnASecondCall() {
		ConfirmedBooking booking = bookAndConfirm("cancel.bearer@example.com");
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);

		ResponseEntity<Void> first =
				restTemplate.exchange("/api/v1/appointments/{id}", HttpMethod.DELETE, new HttpEntity<>(headers), Void.class, booking.appointmentId);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		assertThat(remainingCapacityOf(booking.slotId, booking.branchId, booking.serviceTypeId)).isGreaterThan(0);

		// Calling it again is a no-op success, not an error.
		ResponseEntity<Void> second =
				restTemplate.exchange("/api/v1/appointments/{id}", HttpMethod.DELETE, new HttpEntity<>(headers), Void.class, booking.appointmentId);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void cancelFromWebsite_wrongCredentials_returns404() {
		ConfirmedBooking booking = bookAndConfirm("cancel.wrongcreds@example.com");

		ResponseEntity<ProblemDetail> response = restTemplate.exchange(
				"/api/v1/appointments/{id}?reference={reference}&email={email}",
				HttpMethod.DELETE,
				HttpEntity.EMPTY,
				ProblemDetail.class,
				booking.appointmentId,
				booking.referenceCode,
				"not-the-booking-email@example.com");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void cancellationPreview_bareGetNeverCancelsTheAppointment() {
		ConfirmedBooking booking = bookAndConfirm("preview.antiprefetch@example.com");
		String rawCancellationToken = booking.cancellationToken();

		ResponseEntity<CancellationPreviewResponse> preview =
				restTemplate.getForEntity("/api/v1/cancellations/{token}", CancellationPreviewResponse.class, rawCancellationToken);

		assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(preview.getBody().referenceCode()).isEqualTo(booking.referenceCode);
		assertThat(preview.getBody().alreadyCancelled()).isFalse();
		// The whole point - a bare GET (exactly what an email security scanner does automatically)
		// must never change appointment state.
		assertThat(appointmentRepository.findById(booking.appointmentId).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
	}

	@Test
	void cancellationPreview_unknownToken_returns404() {
		ResponseEntity<ProblemDetail> response =
				restTemplate.getForEntity("/api/v1/cancellations/{token}", ProblemDetail.class, "not-a-real-token");
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void cancellationConfirm_cancelsReleasesCapacity_andIsIdempotentOnReuse() {
		ConfirmedBooking booking = bookAndConfirm("confirm.cancellation@example.com");
		String rawCancellationToken = booking.cancellationToken();

		ResponseEntity<CancellationResultResponse> first =
				restTemplate.postForEntity("/api/v1/cancellations/{token}", null, CancellationResultResponse.class, rawCancellationToken);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(first.getBody().status()).isEqualTo(AppointmentStatus.CANCELLED);

		assertThat(remainingCapacityOf(booking.slotId, booking.branchId, booking.serviceTypeId)).isGreaterThan(0);

		// Reusing the same link just reconfirms "already cancelled" - not an error.
		ResponseEntity<CancellationPreviewResponse> secondPreview =
				restTemplate.getForEntity("/api/v1/cancellations/{token}", CancellationPreviewResponse.class, rawCancellationToken);
		assertThat(secondPreview.getBody().alreadyCancelled()).isTrue();

		ResponseEntity<CancellationResultResponse> second =
				restTemplate.postForEntity("/api/v1/cancellations/{token}", null, CancellationResultResponse.class, rawCancellationToken);
		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(second.getBody().status()).isEqualTo(AppointmentStatus.CANCELLED);
	}

	@Test
	void receiptTokenExpiry_followsRescheduledSlot() {
		ConfirmedBooking booking = bookAndConfirm("receipt.expiry@example.com");
		UUID newSlotId = anotherAvailableSlot(booking.branchId, booking.serviceTypeId, booking.slotId);
		String rawToken = requestRescheduleAndExtractToken(booking, newSlotId);

		ResponseEntity<AppointmentResponse> response =
				restTemplate.postForEntity("/api/v1/reschedule-confirmations/{token}", null, AppointmentResponse.class, rawToken);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		Notification receipt = notificationRepository.findByAppointment_IdAndType(booking.appointmentId, NotificationType.BOOKING_RECEIPT).orElseThrow();
		assertThat(receipt.getTokenExpiresAt()).isEqualTo(AppointmentTimes.startInstant(timeSlotRepository.findById(newSlotId).orElseThrow()));
	}

	@Test
	void receiptTokenExpiry_followsRescheduledSlot_toEarlierSlot() {
		// Book the later of two free slots, then move to the earlier one: the receipt's
		// cancellation link must shrink to the new start, not keep the later one.
		List<AvailabilitySlotResponse> free = availabilityFor(ownBranchId(), firstNewClientServiceTypeId());
		AvailabilitySlotResponse earlier = free.get(0);
		AvailabilitySlotResponse later = free.get(free.size() - 1);
		ConfirmedBooking booking = bookAndConfirm("receipt.earlier@example.com", later.id());
		String rawToken = requestRescheduleAndExtractToken(booking, earlier.id());

		ResponseEntity<AppointmentResponse> response =
				restTemplate.postForEntity("/api/v1/reschedule-confirmations/{token}", null, AppointmentResponse.class, rawToken);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		Instant earlierStart = AppointmentTimes.startInstant(timeSlotRepository.findById(earlier.id()).orElseThrow());
		Instant laterStart = AppointmentTimes.startInstant(timeSlotRepository.findById(later.id()).orElseThrow());
		assertThat(earlierStart).isBefore(laterStart);
		Notification receipt = notificationRepository.findByAppointment_IdAndType(booking.appointmentId, NotificationType.BOOKING_RECEIPT).orElseThrow();
		assertThat(receipt.getTokenExpiresAt()).isEqualTo(earlierStart);
	}

	@Test
	void websiteCancel_afterStart_returns422() {
		ConfirmedBooking booking = bookAndConfirm("cancel.afterstart@example.com");
		moveSlotIntoThePast(booking.slotId);
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);

		ResponseEntity<ProblemDetail> response =
				restTemplate.exchange("/api/v1/appointments/{id}", HttpMethod.DELETE, new HttpEntity<>(headers), ProblemDetail.class, booking.appointmentId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(appointmentRepository.findById(booking.appointmentId).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
	}

	@Test
	void tokenCancel_afterStart_returns410() {
		ConfirmedBooking booking = bookAndConfirm("tokencancel.afterstart@example.com");
		moveSlotIntoThePast(booking.slotId);
		// Confirm sets the receipt to expire at the slot's start, so a started appointment's
		// receipt has expired too - mirror that here.
		Notification receipt = notificationRepository.findByAppointment_IdAndType(booking.appointmentId, NotificationType.BOOKING_RECEIPT).orElseThrow();
		receipt.setTokenExpiry(AppointmentTimes.startInstant(timeSlotRepository.findById(booking.slotId).orElseThrow()));
		notificationRepository.saveAndFlush(receipt);

		ResponseEntity<ProblemDetail> response =
				restTemplate.postForEntity("/api/v1/cancellations/{token}", null, ProblemDetail.class, booking.cancellationToken);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
		assertThat(appointmentRepository.findById(booking.appointmentId).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
	}

	@Test
	void lookup_isCaseAndWhitespaceInsensitive() {
		ConfirmedBooking booking = bookAndConfirm("lookup.case@example.com");

		ResponseEntity<AppointmentResponse> response = restTemplate.getForEntity(
				"/api/v1/appointments/lookup?reference={reference}&email={email}",
				AppointmentResponse.class,
				booking.referenceCode.toLowerCase(Locale.ROOT) + " ",
				" LOOKUP.CASE@example.com");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().id()).isEqualTo(booking.appointmentId);
	}

	@Test
	void getAppointment_byTokenAndByRefEmail_returns200_wrongCredential404() {
		ConfirmedBooking booking = bookAndConfirm("get.appointment@example.com");
		HttpHeaders bearer = new HttpHeaders();
		bearer.setBearerAuth(booking.accessToken);

		ResponseEntity<AppointmentResponse> byToken =
				restTemplate.exchange("/api/v1/appointments/{id}", HttpMethod.GET, new HttpEntity<>(bearer), AppointmentResponse.class, booking.appointmentId);
		assertThat(byToken.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(byToken.getBody().id()).isEqualTo(booking.appointmentId);
		assertThat(byToken.getBody().status()).isEqualTo(AppointmentStatus.CONFIRMED);

		ResponseEntity<AppointmentResponse> byRefEmail = restTemplate.getForEntity(
				"/api/v1/appointments/{id}?reference={reference}&email={email}",
				AppointmentResponse.class,
				booking.appointmentId,
				booking.referenceCode,
				"get.appointment@example.com");
		assertThat(byRefEmail.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(byRefEmail.getBody().id()).isEqualTo(booking.appointmentId);

		ResponseEntity<ProblemDetail> wrongEmail = restTemplate.getForEntity(
				"/api/v1/appointments/{id}?reference={reference}&email={email}",
				ProblemDetail.class,
				booking.appointmentId,
				booking.referenceCode,
				"not-the-booking-email@example.com");
		assertThat(wrongEmail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		ResponseEntity<ProblemDetail> noCredential =
				restTemplate.getForEntity("/api/v1/appointments/{id}", ProblemDetail.class, booking.appointmentId);
		assertThat(noCredential.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		// A valid token for a different id must look exactly like a missing appointment.
		ResponseEntity<ProblemDetail> missing =
				restTemplate.exchange("/api/v1/appointments/{id}", HttpMethod.GET, new HttpEntity<>(bearer), ProblemDetail.class, UUID.randomUUID());
		assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(missing.getBody().getDetail()).isEqualTo(wrongEmail.getBody().getDetail());
	}

	private record ConfirmedBooking(
			UUID appointmentId,
			String referenceCode,
			String accessToken,
			String cancellationToken,
			UUID branchId,
			UUID serviceTypeId,
			UUID slotId,
			LocalTime slotStartTime) {
	}

	private ConfirmedBooking bookAndConfirm(String email) {
		return bookAndConfirm(email, firstAvailableSlotId(ownBranchId(), firstNewClientServiceTypeId()));
	}

	private ConfirmedBooking bookAndConfirm(String email, UUID slotId) {
		UUID branchId = ownBranchId();
		UUID serviceTypeId = firstNewClientServiceTypeId();

		NewClientBookingRequest request = new NewClientBookingRequest(ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Management Test", email, "+27825550000");
		ConfirmationResultResponse confirmed = BookingFixtures.bookAndConfirm(restTemplate, request);
		// Only available here, transiently - the receipt's actionLink is the one place the raw
		// cancellation token exists outside the customer's own inbox (only its hash is persisted).
		String rawCancellationToken = extractToken(confirmed.receiptEmail().actionLink());

		return new ConfirmedBooking(
				confirmed.appointmentId(),
				confirmed.referenceCode(),
				confirmed.accessToken(),
				rawCancellationToken,
				branchId,
				serviceTypeId,
				slotId,
				confirmed.startTime());
	}

	/** Requests a reschedule via the bearer-token credential and pulls the raw confirm token out
	 * of the returned simulated email - mirrors how bookAndConfirm extracts the booking-hold
	 * token, for the same reason (only the raw token, not its hash, is ever returned to a caller). */
	private String requestRescheduleAndExtractToken(ConfirmedBooking booking, UUID newSlotId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(booking.accessToken);
		RescheduleRequestResponse response = restTemplate.postForObject(
				"/api/v1/appointments/{id}/reschedule-request", new HttpEntity<>(new RescheduleRequest(newSlotId), headers), RescheduleRequestResponse.class, booking.appointmentId);
		return extractToken(response.simulatedEmail().actionLink());
	}

	private void backdateSlotToStartWithinTheNoticeWindow(UUID slotId) {
		var slot = timeSlotRepository.findById(slotId).orElseThrow();
		ReflectionTestUtils.setField(slot, "slotDate", LocalDate.now(AppTimeZone.ZONE));
		ReflectionTestUtils.setField(slot, "startTime", LocalTime.now(AppTimeZone.ZONE).plusMinutes(30));
		timeSlotRepository.saveAndFlush(slot);
	}

	// Same slot-rewrite as above, but fully in the past. Shifting the date back keeps the start
	// time, so each rewritten slot stays unique and clear of the generated (future) window.
	private void moveSlotIntoThePast(UUID slotId) {
		var slot = timeSlotRepository.findById(slotId).orElseThrow();
		ReflectionTestUtils.setField(slot, "slotDate", slot.getSlotDate().minusDays(400));
		timeSlotRepository.saveAndFlush(slot);
	}

	// The last branch, not the first: the other booking tests book the first branch's next-Monday
	// slots, and this class books the earliest free slot of a whole week, so sharing a branch would
	// make either side run out of slots depending on which class the build happens to run first.
	private UUID ownBranchId() {
		BranchResponse[] branches = restTemplate.getForObject("/api/v1/branches", BranchResponse[].class);
		return branches[branches.length - 1].id();
	}

	private UUID firstNewClientServiceTypeId() {
		return restTemplate.getForObject("/api/v1/service-types?clientType=NEW_CLIENT", ServiceTypeResponse[].class)[0].id();
	}

	private UUID firstAvailableSlotId(UUID branchId, UUID serviceTypeId) {
		return availabilityFor(branchId, serviceTypeId).get(0).id();
	}

	private UUID anotherAvailableSlot(UUID branchId, UUID serviceTypeId, UUID excludingSlotId) {
		return availabilityFor(branchId, serviceTypeId).stream()
				.map(AvailabilitySlotResponse::id)
				.filter(id -> !id.equals(excludingSlotId))
				.findFirst()
				.orElseThrow();
	}

	private int remainingCapacityOf(UUID slotId, UUID branchId, UUID serviceTypeId) {
		return BookingFixtures.remainingCapacityOf(availabilityFor(branchId, serviceTypeId), slotId);
	}

	// Pooled across a work week, not just "next Monday" - with DEFAULT_CAPACITY now 1 (one
	// appointment per slot, see TimeSlotGenerationService), a single day's ~16 slots isn't enough
	// supply for every test in this file to each book its own without colliding.
	private List<AvailabilitySlotResponse> availabilityFor(UUID branchId, UUID serviceTypeId) {
		LocalDate nextMonday = LocalDate.now(AppTimeZone.ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
		List<AvailabilitySlotResponse> pooled = new ArrayList<>();
		for (int offset = 0; offset < 5; offset++) {
			AvailabilitySlotResponse[] slots = restTemplate.getForObject(
					"/api/v1/branches/{id}/availability?date={date}&serviceTypeId={serviceTypeId}",
					AvailabilitySlotResponse[].class,
					branchId,
					nextMonday.plusDays(offset),
					serviceTypeId);
			pooled.addAll(List.of(slots));
		}
		return pooled;
	}

}
