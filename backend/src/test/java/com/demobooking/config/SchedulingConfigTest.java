package com.demobooking.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** The companion to SchedulingDisabledInTestsTest: proves production (property on, or absent)
 * still schedules, so turning it off in tests can't hide a broken production setup. */
class SchedulingConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(SchedulingConfig.class);

	@Test
	void registersTheSchedulingPostProcessor_whenEnabled() {
		contextRunner
				.withPropertyValues("app.scheduling.enabled=true")
				.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
	}

	@Test
	void registersTheSchedulingPostProcessor_whenThePropertyIsAbsent() {
		contextRunner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
	}

}
