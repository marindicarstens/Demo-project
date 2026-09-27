package com.demobooking.branch;

import com.demobooking.common.AppTimeZone;
import com.demobooking.config.AppProperties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates the rolling window of bookable time slots - see docs/SEED-DATA.md § Time slots.
 * Slots aren't static seed data because "the next N business days" is a moving target; this
 * keeps the window filled going forward without a human re-seeding it.
 *
 * Safe with several backend instances: a run holds a Postgres session advisory lock, and an
 * instance that can't take it skips the run. Each day is written in its own transaction, so one
 * failing day (e.g. a unique-constraint clash) rolls back alone and the rest still get generated.
 */
@Service
class TimeSlotGenerationService {

	private static final Logger log = LoggerFactory.getLogger(TimeSlotGenerationService.class);

	/** One appointment per slot - deliberately simplified for the demo (see docs/SEED-DATA.md §
	 * Time slots): a slot is either bookable or it isn't, with no "2 of 3 remaining" capacity
	 * concept to model or explain. A real deployment would set this from actual branch capacity
	 * planning data instead of one fixed constant. */
	private static final int DEFAULT_CAPACITY = 1;

	/** Any constant unique to this job; every instance must use the same one. */
	private static final long GENERATION_LOCK_KEY = 0x736C6F7447656EL; // "slotGen"

	private final BranchRepository branchRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final TimeSlotRepository timeSlotRepository;
	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate dayTransaction;
	private final Clock clock;
	private final int rollingWindowDays;

	/**
	 * Every branch in the seed data shares Mon-Fri hours but closes earlier on Saturday (see
	 * docs/SEED-DATA.md). The BRANCH table only models one opens/closes pair, so this is one
	 * setting (app.slots.saturday-closing-time) rather than a schema change - correct for this
	 * seed set; would need a real per-day-of-week hours model if branches ever need genuinely
	 * different Saturday hours from each other.
	 */
	private final LocalTime saturdayClosingTime;

	TimeSlotGenerationService(
			BranchRepository branchRepository,
			ServiceTypeRepository serviceTypeRepository,
			TimeSlotRepository timeSlotRepository,
			JdbcTemplate jdbcTemplate,
			PlatformTransactionManager transactionManager,
			Clock clock,
			AppProperties appProperties) {
		this.branchRepository = branchRepository;
		this.serviceTypeRepository = serviceTypeRepository;
		this.timeSlotRepository = timeSlotRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.dayTransaction = new TransactionTemplate(transactionManager);
		this.dayTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.clock = clock;
		this.rollingWindowDays = appProperties.slots().rollingWindowDays();
		this.saturdayClosingTime = appProperties.slots().saturdayClosingTime();
	}

	@EventListener(ApplicationReadyEvent.class)
	void generateOnStartup() {
		ensureWindowFilled();
	}

	/** Re-runs daily so the rolling window keeps extending forward as days pass. */
	@Scheduled(cron = "${app.slots.generation-cron}")
	void generateDaily() {
		ensureWindowFilled();
	}

	// The lock is session-scoped, so the connection that takes it is pinned until the unlock in
	// finally. The day transactions run on other pool connections, which is fine: the lock
	// belongs to the pinned session, and that stays open for the whole run.
	void ensureWindowFilled() {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			if (!callLockFunction(connection, "pg_try_advisory_lock")) {
				log.info("Skipping slot generation: another instance is generating slots");
				return null;
			}
			try {
				fillWindow();
			} finally {
				if (!callLockFunction(connection, "pg_advisory_unlock")) {
					log.warn("Slot generation lock {} was not held when releasing it", GENERATION_LOCK_KEY);
				}
			}
			return null;
		});
	}

	private void fillWindow() {
		List<Branch> branches = branchRepository.findByActiveTrueOrderByNameAsc();
		List<ServiceType> serviceTypes = serviceTypeRepository.findAllByOrderByNameAsc();
		LocalDate today = LocalDate.ofInstant(clock.instant(), AppTimeZone.ZONE);

		int generated = 0;
		for (Branch branch : branches) {
			for (ServiceType serviceType : serviceTypes) {
				for (int offset = 0; offset < rollingWindowDays; offset++) {
					LocalDate date = today.plusDays(offset);
					// Caught outside the day's transaction, so exceptions translated at commit
					// time are covered too, and only that day rolls back.
					try {
						generated += dayTransaction.execute(status -> ensureDayFilled(branch, serviceType, date));
					} catch (DataIntegrityViolationException e) {
						log.info("Slots for branch {}, service type {} on {} already exist; skipping that day", branch.getId(), serviceType.getId(), date);
					} catch (DataAccessException e) {
						log.warn("Could not generate slots for branch {}, service type {} on {}; continuing", branch.getId(), serviceType.getId(), date, e);
					}
				}
			}
		}
		if (generated > 0) {
			log.info("Generated {} time slots to fill the {}-day rolling window", generated, rollingWindowDays);
		}
	}

	private static boolean callLockFunction(Connection connection, String function) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT " + function + "(?)")) {
			statement.setLong(1, GENERATION_LOCK_KEY);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getBoolean(1);
			}
		}
	}

	private int ensureDayFilled(Branch branch, ServiceType serviceType, LocalDate date) {
		if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
			return 0;
		}
		if (timeSlotRepository.existsByBranchAndServiceTypeAndSlotDate(branch, serviceType, date)) {
			return 0; // idempotent - already generated for this branch/service/date
		}

		LocalTime closingTime =
				date.getDayOfWeek() == DayOfWeek.SATURDAY
						? minTime(branch.getClosesAt(), saturdayClosingTime)
						: branch.getClosesAt();

		List<TimeSlot> daySlots = new ArrayList<>();
		LocalTime slotStart = branch.getOpensAt();
		while (!slotStart.plusMinutes(serviceType.getDurationMinutes()).isAfter(closingTime)) {
			daySlots.add(new TimeSlot(branch, serviceType, date, slotStart, DEFAULT_CAPACITY));
			slotStart = slotStart.plusMinutes(serviceType.getDurationMinutes());
		}
		timeSlotRepository.saveAll(daySlots);
		return daySlots.size();
	}

	private static LocalTime minTime(LocalTime a, LocalTime b) {
		return a.isBefore(b) ? a : b;
	}

}
