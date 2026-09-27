package com.demobooking;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The one setup every integration test shares: a real server on a random port, real Postgres
 * and Redis containers, the "test" profile (application-test.yml - scheduling off), and a
 * MutableClock reset after every test. One annotation instead of several keeps every class on
 * the same cached Spring context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ExtendWith(MutableClockResetExtension.class)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public @interface IntegrationTest {
}
