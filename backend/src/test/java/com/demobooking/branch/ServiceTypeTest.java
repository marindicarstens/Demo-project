package com.demobooking.branch;

import static org.assertj.core.api.Assertions.assertThat;

import com.demobooking.customer.ClientType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ServiceTypeTest {

	private static ServiceType offeredTo(ApplicableClientType applicableClientType) {
		ServiceType serviceType = new ServiceType();
		ReflectionTestUtils.setField(serviceType, "applicableClientType", applicableClientType);
		return serviceType;
	}

	@Test
	void appliesTo_matchesOnlyTheNamedFlow() {
		assertThat(offeredTo(ApplicableClientType.NEW_CLIENT).appliesTo(ClientType.NEW_CLIENT)).isTrue();
		assertThat(offeredTo(ApplicableClientType.NEW_CLIENT).appliesTo(ClientType.EXISTING_CLIENT)).isFalse();
		assertThat(offeredTo(ApplicableClientType.EXISTING_CLIENT).appliesTo(ClientType.EXISTING_CLIENT)).isTrue();
		assertThat(offeredTo(ApplicableClientType.EXISTING_CLIENT).appliesTo(ClientType.NEW_CLIENT)).isFalse();
	}

	@Test
	void appliesTo_bothCoversEveryFlow() {
		for (ClientType clientType : ClientType.values()) {
			assertThat(offeredTo(ApplicableClientType.BOTH).appliesTo(clientType)).isTrue();
		}
	}

}
