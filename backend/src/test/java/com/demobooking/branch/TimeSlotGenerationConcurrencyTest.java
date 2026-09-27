package com.demobooking.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;

import com.demobooking.IntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Slot generation run by several instances at once (see TimeSlotGenerationService's Javadoc):
 * the advisory lock keeps runs from duplicating a day, and per-day transactions keep one failing
 * day from blocking the rest. Only days with no appointments or notifications are deleted and
 * regenerated, so other tests' bookings are never touched.
 */
@IntegrationTest
class TimeSlotGenerationConcurrencyTest {

	@Autowired
	private TimeSlotGenerationService generationService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	private TimeSlotRepository timeSlotRepository;

	/** A branch+service type with two consecutive, fully unbooked generated days. */
	private record FreeDays(UUID branchId, UUID serviceTypeId, LocalDate day, int daySlots, int nextDaySlots) {
	}

	@AfterEach
	void restoreWindow() {
		reset(timeSlotRepository);
		generationService.ensureWindowFilled();
	}

	@Test
	void concurrentRuns_generateTheDayOnce() throws Exception {
		FreeDays free = findFreeDays();
		deleteDay(free, free.day());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Void> run = () -> {
			start.await();
			generationService.ensureWindowFilled();
			return null;
		};
		try {
			Future<Void> first = executor.submit(run);
			Future<Void> second = executor.submit(run);
			start.countDown();
			// get() rethrows anything either run threw.
			first.get(30, TimeUnit.SECONDS);
			second.get(30, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(slotCount(free, free.day())).isEqualTo(free.daySlots());
		assertThat(advisoryLockCount()).isZero();
	}

	@Test
	void oneDayFailing_doesNotBlockLaterDays() {
		FreeDays free = findFreeDays();
		LocalDate failingDay = free.day();
		LocalDate nextDay = failingDay.plusDays(1);
		deleteDay(free, failingDay);
		deleteDay(free, nextDay);
		doAnswer(invocation -> {
			Iterable<TimeSlot> slots = invocation.getArgument(0);
			if (StreamSupport.stream(slots.spliterator(), false).allMatch(slot -> slot.getSlotDate().equals(failingDay))) {
				throw new DataIntegrityViolationException("test");
			}
			// The spy wraps a JDK proxy, so there's no real method to call - its default answer
			// is what delegates to the actual repository.
			return mockingDetails(timeSlotRepository).getMockCreationSettings().getDefaultAnswer().answer(invocation);
		}).when(timeSlotRepository).saveAll(any());

		assertThatCode(generationService::ensureWindowFilled).doesNotThrowAnyException();

		assertThat(slotCount(free, failingDay)).isZero();
		assertThat(slotCount(free, nextDay)).isEqualTo(free.nextDaySlots());
		assertThat(advisoryLockCount()).isZero();
	}

	private FreeDays findFreeDays() {
		return jdbcTemplate.queryForObject("""
				WITH free_day AS (
				  SELECT t.branch_id, t.service_type_id, t.slot_date, count(*) AS slots
				  FROM time_slot t
				  LEFT JOIN appointment a ON a.time_slot_id = t.id
				  LEFT JOIN notification n ON n.new_time_slot_id = t.id
				  WHERE t.slot_date > CURRENT_DATE
				  GROUP BY t.branch_id, t.service_type_id, t.slot_date
				  HAVING count(a.id) = 0 AND count(n.id) = 0
				)
				SELECT d.branch_id, d.service_type_id, d.slot_date, d.slots, next_day.slots AS next_slots
				FROM free_day d
				JOIN free_day next_day ON next_day.branch_id = d.branch_id
				  AND next_day.service_type_id = d.service_type_id
				  AND next_day.slot_date = d.slot_date + 1
				ORDER BY d.slot_date DESC
				LIMIT 1
				""",
				(rs, row) -> new FreeDays(
						rs.getObject("branch_id", UUID.class),
						rs.getObject("service_type_id", UUID.class),
						rs.getObject("slot_date", LocalDate.class),
						rs.getInt("slots"),
						rs.getInt("next_slots")));
	}

	private void deleteDay(FreeDays free, LocalDate day) {
		jdbcTemplate.update(
				"DELETE FROM time_slot WHERE branch_id = ? AND service_type_id = ? AND slot_date = ?",
				free.branchId(), free.serviceTypeId(), day);
	}

	private int slotCount(FreeDays free, LocalDate day) {
		return jdbcTemplate.queryForObject(
				"SELECT count(*) FROM time_slot WHERE branch_id = ? AND service_type_id = ? AND slot_date = ?",
				Integer.class, free.branchId(), free.serviceTypeId(), day);
	}

	private int advisoryLockCount() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'", Integer.class);
	}

}
