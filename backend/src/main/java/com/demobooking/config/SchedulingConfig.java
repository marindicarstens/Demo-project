package com.demobooking.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling is switchable so tests can drive the expiry sweep directly: with the real
 * @Scheduled sweep also running each interval, any run could race a test's own sweep() call on
 * the same rows and make the result depend on timing. On unless explicitly disabled, so
 * production never needs the property.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
