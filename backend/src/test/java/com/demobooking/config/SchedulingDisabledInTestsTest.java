package com.demobooking.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * The test profile turns scheduling off, so no @Scheduled sweep can race a test's own direct
 * sweep() call. Without the post-processor, no @Scheduled method is ever registered.
 */
@IntegrationTest
class SchedulingDisabledInTestsTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void noScheduledMethodsAreRegistered_whenSchedulingIsDisabled() {
		assertThat(applicationContext.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(SchedulingConfig.class)).isEmpty();
	}

}
