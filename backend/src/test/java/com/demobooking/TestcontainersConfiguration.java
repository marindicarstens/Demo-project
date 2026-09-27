package com.demobooking;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

// Public: shared test infrastructure imported from every bounded-context test package
// (com.demobooking.branch, .booking, ...), not just the top-level application test.
@TestConfiguration(proxyBeanMethods = false)
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
public class TestcontainersConfiguration {
}
