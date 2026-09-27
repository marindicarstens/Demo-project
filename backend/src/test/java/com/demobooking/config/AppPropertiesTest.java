package com.demobooking.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** A bad secret or TTL must stop startup, not surface on the first request that uses it. */
class AppPropertiesTest {

	private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef";

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		validatorFactory.close();
	}

	@Test
	void aStrongSecret_isValid() {
		assertThat(validator.validate(withSecret(STRONG_SECRET))).isEmpty();
	}

	@Test
	void theChangemePlaceholder_isRejected() {
		assertThat(messages(validator.validate(withSecret("changeme")))).anyMatch(m -> m.contains("jwt-signing-secret"));
	}

	@Test
	void aSecretUnder32Bytes_isRejected() {
		assertThat(messages(validator.validate(withSecret(STRONG_SECRET.substring(1))))).anyMatch(m -> m.contains("jwt-signing-secret"));
	}

	@Test
	void anEmptySecret_isRejected() {
		assertThat(messages(validator.validate(withSecret("")))).anyMatch(m -> m.contains("jwt-signing-secret"));
	}

	@Test
	void anIdempotencyTtlLongerThanTheTokenTtl_isRejected() {
		AppProperties properties = properties(
				STRONG_SECRET, new AppProperties.Booking(Duration.ofMinutes(1), Duration.ofHours(2), Duration.ofMinutes(30), Duration.ofMinutes(2)));

		assertThat(messages(validator.validate(properties))).anyMatch(m -> m.contains("idempotency-key-ttl"));
	}

	@Test
	void startup_withThePlaceholderSecret_failsWithABindingValidationError() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
				.withUserConfiguration(EnableAppProperties.class)
				.withPropertyValues(validPropertiesWithSecret("changeme"))
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("jwt-signing-secret");
				});
	}

	@Test
	void startup_withValidProperties_bindsThem() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
				.withUserConfiguration(EnableAppProperties.class)
				.withPropertyValues(validPropertiesWithSecret(STRONG_SECRET))
				.run(context -> {
					AppProperties properties = context.getBean(AppProperties.class);
					assertThat(properties.booking().confirmationTokenTtl()).isEqualTo(Duration.ofMinutes(1));
					assertThat(properties.booking().rescheduleMinNotice()).isEqualTo(Duration.ofHours(2));
					assertThat(properties.slots().saturdayClosingTime()).isEqualTo(LocalTime.of(13, 0));
					assertThat(properties.rateLimit().lookup().window()).isEqualTo(Duration.ofMinutes(15));
				});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AppProperties.class)
	static class EnableAppProperties {
	}

	private static String[] validPropertiesWithSecret(String secret) {
		return new String[] {
				"app.jwt-signing-secret=" + secret,
				"app.frontend-origin=http://localhost:4200",
				"app.booking.confirmation-token-ttl=1",
				"app.booking.reschedule-min-notice=2",
				"app.booking.access-token-ttl=30",
				"app.booking.idempotency-key-ttl=1",
				"app.rate-limit.booking-create.max-attempts=200",
				"app.rate-limit.booking-create.window=PT15M",
				"app.rate-limit.lookup.max-attempts=200",
				"app.rate-limit.lookup.window=PT15M",
				"app.rate-limit.directory-validation.max-attempts=5",
				"app.rate-limit.directory-validation.window=PT15M",
				"app.sweep.interval=PT15S",
				"app.slots.generation-cron=0 0 1 * * *",
				"app.slots.rolling-window-days=14",
				"app.slots.saturday-closing-time=13:00",
				"app.cache.ttl=PT10M"
		};
	}

	private static AppProperties withSecret(String secret) {
		return properties(secret, new AppProperties.Booking(Duration.ofMinutes(1), Duration.ofHours(2), Duration.ofMinutes(30), Duration.ofMinutes(1)));
	}

	private static AppProperties properties(String secret, AppProperties.Booking booking) {
		AppProperties.Limit limit = new AppProperties.Limit(200, Duration.ofMinutes(15));
		return new AppProperties(
				"http://localhost:4200",
				secret,
				booking,
				new AppProperties.RateLimits(limit, limit, limit),
				new AppProperties.Sweep(Duration.ofSeconds(15)),
				new AppProperties.Slots("0 0 1 * * *", 14, LocalTime.of(13, 0)),
				new AppProperties.Cache(Duration.ofMinutes(10)));
	}

	private static Set<String> messages(Set<ConstraintViolation<AppProperties>> violations) {
		return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
	}

}
