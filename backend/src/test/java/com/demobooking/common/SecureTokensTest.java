package com.demobooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecureTokensTest {

	@Test
	void generateToken_is256RandomBitsInUrlSafeBase64() {
		String token = SecureTokens.generateToken();

		assertThat(token).matches("[A-Za-z0-9_-]{43}");
		assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
		assertThat(SecureTokens.generateToken()).isNotEqualTo(token);
	}

	@Test
	void hash_isTheLowercaseHexSha256OfTheToken() {
		// Known SHA-256 test vector for "abc".
		assertThat(SecureTokens.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
		assertThat(SecureTokens.hash("abc")).isEqualTo(SecureTokens.hash("abc"));
	}

	@Test
	void generateReferenceCode_usesOnlyUnambiguousCharacters() {
		for (int i = 0; i < 200; i++) {
			assertThat(SecureTokens.generateReferenceCode()).matches("BR-[A-HJ-NP-Z2-9]{6}");
		}
	}

}
