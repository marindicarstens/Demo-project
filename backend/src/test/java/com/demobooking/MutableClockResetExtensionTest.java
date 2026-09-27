package com.demobooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

/** A clock moved forward in one test must be back to real time in the next, same context. */
@IntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MutableClockResetExtensionTest {

	@Autowired
	private MutableClock clock;

	@Test
	@Order(1)
	void advancingTheClock_movesItForward() {
		clock.advance(Duration.ofHours(1));

		assertThat(Duration.between(Instant.now(), clock.instant())).isGreaterThan(Duration.ofMinutes(59));
	}

	@Test
	@Order(2)
	void theNextTest_seesTheResetTime() {
		assertThat(Duration.between(Instant.now(), clock.instant()).abs()).isLessThan(Duration.ofMinutes(1));
	}

}
