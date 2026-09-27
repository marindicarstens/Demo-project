package com.demobooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demobooking.IntegrationTest;
import com.demobooking.MutableClock;
import com.demobooking.booking.dto.AppointmentHoldResponse;
import com.demobooking.booking.dto.ConfirmationResultResponse;
import com.demobooking.booking.dto.NewClientBookingRequest;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the race Appointment's @Version Javadoc describes: ExpirySweepService.sweepOneExpiredHold
 * loads its own copy of the appointment (via getReferenceById, inside its own transaction) before
 * BookingService.confirm() commits, then tries to write markExpired() after confirm() has already
 * committed CONFIRMED. True wall-clock threading can't make that interleaving land deterministically
 * in a test, so this reproduces the same precondition a different way: read a detached copy of the
 * appointment BEFORE confirming (that's the sweep's stale view), confirm for real via the API
 * (commits status=CONFIRMED, bumping version), then replay the sweep's own markExpired()+save on
 * that stale, now-version-behind copy - exactly what sweepOneExpiredHold does with its stale read.
 * Without @Version this save would have silently succeeded and left the appointment EXPIRED despite
 * being CONFIRMED moments earlier; with it, the save must fail with an optimistic-lock exception
 * (ExpirySweepService.sweepOneExpiredHold catches exactly this and skips the item, see its Javadoc)
 * and the already-committed CONFIRMED status must survive untouched.
 */
@IntegrationTest
class ConfirmVsExpirySweepConcurrencyTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private TimeSlotRepository timeSlotRepository;

	@Autowired
	private ExpirySweepService expirySweepService;

	@Autowired
	private MutableClock clock;

	@Test
	void sweepLosingTheRaceToConfirm_failsWithOptimisticLock_insteadOfSilentlyRevertingAConfirmedAppointmentToExpired() {
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

		NewClientBookingRequest request = new NewClientBookingRequest(
				ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Sweep Race Test", "sweep.race.test@example.com", "+27825559999");
		AppointmentHoldResponse hold = restTemplate.postForObject("/api/v1/appointments", request, AppointmentHoldResponse.class);
		String rawToken = hold.simulatedEmail().actionLink().substring(hold.simulatedEmail().actionLink().lastIndexOf('/') + 1);

		// The sweep's stale read: a detached copy, taken before confirm() below ever runs, exactly
		// like sweepOneExpiredHold loading the row inside its own transaction before confirm commits.
		Appointment staleCopyAsSweepWouldSeeIt = appointmentRepository.findById(hold.appointmentId()).orElseThrow();

		ConfirmationResultResponse confirmed =
				restTemplate.postForObject("/api/v1/confirmations/{token}", null, ConfirmationResultResponse.class, rawToken);
		assertThat(confirmed.status()).isEqualTo(AppointmentStatus.CONFIRMED);

		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			staleCopyAsSweepWouldSeeIt.markExpired();
			appointmentRepository.saveAndFlush(staleCopyAsSweepWouldSeeIt);
		})).isInstanceOf(OptimisticLockingFailureException.class);

		Appointment afterTheLostRace = appointmentRepository.findById(hold.appointmentId()).orElseThrow();
		assertThat(afterTheLostRace.getStatus())
				.as("confirm()'s already-committed write must survive a losing, version-stale sweep write")
				.isEqualTo(AppointmentStatus.CONFIRMED);
	}

	/**
	 * The other half of the race: the sweep's list query saw the hold before confirm committed,
	 * but its per-item transaction re-reads the appointment *after* - so no version is stale and
	 * @Version can't catch it. The in-transaction status re-check (and markExpired's own guard)
	 * must, or a just-confirmed appointment is expired and its slot released.
	 */
	@Test
	void sweepItemTransaction_afterConfirmCommitted_skipsAndLeavesConfirmed() {
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

		NewClientBookingRequest request = new NewClientBookingRequest(
				ClientType.NEW_CLIENT, branchId, serviceTypeId, slotId, "Sweep Refetch Test", "sweep.refetch.test@example.com", "+27825559998");
		AppointmentHoldResponse hold = restTemplate.postForObject("/api/v1/appointments", request, AppointmentHoldResponse.class);
		String rawToken = hold.simulatedEmail().actionLink().substring(hold.simulatedEmail().actionLink().lastIndexOf('/') + 1);
		Notification confirmationRequest = notificationRepository.findAll().stream()
				.filter(n -> n.getAppointment().getId().equals(hold.appointmentId()) && n.getType() == NotificationType.CONFIRMATION_REQUEST)
				.findFirst()
				.orElseThrow();

		ConfirmationResultResponse confirmed =
				restTemplate.postForObject("/api/v1/confirmations/{token}", null, ConfirmationResultResponse.class, rawToken);
		assertThat(confirmed.status()).isEqualTo(AppointmentStatus.CONFIRMED);

		// Make it look lapsed, as the sweep's earlier list read would have seen it.
		clock.advance(Duration.ofMinutes(2));

		expirySweepService.sweepOneExpiredHold(confirmationRequest.getId());

		assertThat(appointmentRepository.findById(hold.appointmentId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		assertThat(timeSlotRepository.findById(slotId).orElseThrow().getBookedCount()).isEqualTo(1);
		boolean hasExpiryNotice = notificationRepository.findAll().stream()
				.anyMatch(n -> n.getAppointment().getId().equals(hold.appointmentId()) && n.getType() == NotificationType.EXPIRY_NOTICE);
		assertThat(hasExpiryNotice).isFalse();
	}
}
