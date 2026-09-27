package com.demobooking.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;

/**
 * Confirmation/cancellation tokens: cryptographically random, hashed at rest. Reference codes:
 * high-entropy, not sequential.
 */
public final class SecureTokens {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String REFERENCE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I - easy to misread aloud

	// Spring Security's own generator: 256 random bits from KeyGenerators' shared SecureRandom,
	// URL-safe base64 encoded. This is the one place a raw token is produced.
	private static final StringKeyGenerator TOKEN_GENERATOR =
			new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32);

	private SecureTokens() {
	}

	/** The raw token embedded in a link - see TOKEN_GENERATOR. */
	public static String generateToken() {
		return TOKEN_GENERATOR.generateKey();
	}

	/** SHA-256 hex digest - only this, never the raw token, is ever stored. */
	public static String hash(String rawToken) {
		return new String(Hex.encode(sha256(rawToken)));
	}

	/** The raw SHA-256 digest of the UTF-8 bytes - the one place the algorithm is named. Spring
	 * Security's crypto module has no deterministic digest of its own (its PasswordEncoders salt
	 * every call, which would break lookup-by-hash), so this stays on MessageDigest. */
	public static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 must be available on every JVM", e);
		}
	}

	/** e.g. "BR-7F3K9Q". Collision handling is the caller's job
	 * (regenerate and retry against the DB unique constraint), not this method's. */
	public static String generateReferenceCode() {
		StringBuilder suffix = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			suffix.append(REFERENCE_CODE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_CODE_ALPHABET.length())));
		}
		return "BR-" + suffix;
	}

}
