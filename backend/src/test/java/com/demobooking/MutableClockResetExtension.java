package com.demobooking;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * The cached Spring context shares one MutableClock across every test class, so a test that
 * moves time forward must not leak that into the next one. Registered by the composed
 * integration-test annotations, so no class can forget the reset.
 */
public class MutableClockResetExtension implements AfterEachCallback {

	@Override
	public void afterEach(ExtensionContext context) {
		SpringExtension.getApplicationContext(context).getBean(MutableClock.class).reset();
	}

}
