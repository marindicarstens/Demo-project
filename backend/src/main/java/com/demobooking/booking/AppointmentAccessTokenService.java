package com.demobooking.booking;

import com.demobooking.common.SecureTokens;
import com.demobooking.config.AppProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * The short-lived, appointment-scoped access token issued on confirmation. Deliberately narrow:
 * the only claim is the appointment ID, not a general customer identity - it authenticates
 * "control of this appointment for a limited time," not a full account/session. Used for the immediate reschedule/cancel actions right after
 * confirming; once it expires, the customer falls back to reference+email lookup.
 */
@Service
class AppointmentAccessTokenService {

	private final SecretKey signingKey;
	private final Duration ttl;
	private final Clock clock;

	AppointmentAccessTokenService(AppProperties appProperties, Clock clock) {
		// AppProperties already rejects short or placeholder secrets; SHA-256 here is only key
		// derivation, giving a fixed 256-bit HMAC key (RFC 7518 §3.2) from the configured string.
		this.signingKey = Keys.hmacShaKeyFor(SecureTokens.sha256(appProperties.jwtSigningSecret()));
		this.ttl = appProperties.booking().accessTokenTtl();
		this.clock = clock;
	}

	record IssuedToken(String token, Instant expiresAt) {
	}

	IssuedToken issueFor(UUID appointmentId) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plus(ttl);
		String token = Jwts.builder()
				.subject(appointmentId.toString())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(expiresAt))
				.signWith(signingKey)
				.compact();
		return new IssuedToken(token, expiresAt);
	}

	/** Empty for any invalid/expired/mis-signed token - a caller treats that the same as "no token
	 * offered" and falls back to reference+email, rather than distinguishing why it failed. */
	Optional<UUID> verify(String rawToken) {
		try {
			String subject = Jwts.parser()
					.verifyWith(signingKey)
					// Same clock as issuing, so a test that moves time forward expires tokens too.
					.clock(() -> Date.from(clock.instant()))
					.build().parseSignedClaims(rawToken).getPayload().getSubject();
			return Optional.of(UUID.fromString(subject));
		} catch (JwtException | IllegalArgumentException invalidToken) {
			return Optional.empty();
		}
	}

}
