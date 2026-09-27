package com.demobooking.config;

import com.demobooking.common.AppTimeZone;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one source of "now" for every service, so tests can move time forward instead of
 * backdating rows by reflection. Zoned to SAST, so a LocalDate derived from it is a branch's
 * local date, not the container's (typically UTC).
 */
@Configuration
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.system(AppTimeZone.ZONE);
	}

}
