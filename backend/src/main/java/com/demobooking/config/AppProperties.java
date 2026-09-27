package com.demobooking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;

/**
 * Every "app.*" setting in one validated place, so a bad value stops startup instead of
 * surfacing on the first request that reads it. Defaults live in application.yml; the Docker
 * profile maps the APP_* environment variables onto these keys.
 */
@ConfigurationProperties("app")
@Validated
public record AppProperties(
		@NotBlank String frontendOrigin,
		@NotNull String jwtSigningSecret,
		@Valid @NotNull Booking booking,
		@Valid @NotNull RateLimits rateLimit,
		@Valid @NotNull Sweep sweep,
		@Valid @NotNull Slots slots,
		@Valid @NotNull Cache cache) {

	private static final int MIN_JWT_SECRET_BYTES = 32;

	// 32 bytes is HMAC-SHA256's key size; "changeme" was the old .env.example placeholder.
	@AssertTrue(message = "app.jwt-signing-secret (APP_JWT_SIGNING_SECRET) must be at least 32 bytes and not a placeholder - generate one with: openssl rand -base64 32")
	public boolean isJwtSigningSecretUsable() {
		return jwtSigningSecret != null
				&& jwtSigningSecret.getBytes(StandardCharsets.UTF_8).length >= MIN_JWT_SECRET_BYTES
				&& !"changeme".equalsIgnoreCase(jwtSigningSecret.trim());
	}

	public record Booking(
			@NotNull @DurationUnit(ChronoUnit.MINUTES) Duration confirmationTokenTtl,
			@NotNull @DurationUnit(ChronoUnit.HOURS) Duration rescheduleMinNotice,
			@NotNull @DurationUnit(ChronoUnit.MINUTES) Duration accessTokenTtl,
			@NotNull @DurationUnit(ChronoUnit.MINUTES) Duration idempotencyKeyTtl) {

		// A stored replay holds the raw confirmation link, so it must never outlive that token.
		@AssertTrue(message = "app.booking.idempotency-key-ttl must not exceed app.booking.confirmation-token-ttl")
		public boolean isIdempotencyKeyTtlWithinTokenTtl() {
			return idempotencyKeyTtl == null || confirmationTokenTtl == null || idempotencyKeyTtl.compareTo(confirmationTokenTtl) <= 0;
		}
	}

	public record RateLimits(@Valid @NotNull Limit bookingCreate, @Valid @NotNull Limit lookup, @Valid @NotNull Limit directoryValidation) {
	}

	public record Limit(@Positive long maxAttempts, @NotNull Duration window) {
	}

	public record Sweep(@NotNull Duration interval) {
	}

	public record Slots(
			@NotBlank String generationCron,
			@Positive int rollingWindowDays,
			@NotNull @DateTimeFormat(pattern = "HH:mm") LocalTime saturdayClosingTime) {
	}

	public record Cache(@NotNull Duration ttl) {
	}

}
