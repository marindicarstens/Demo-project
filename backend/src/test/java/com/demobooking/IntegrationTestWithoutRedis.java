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
 * Same as {@link IntegrationTest} but with no Redis container, for tests that need Redis to be
 * unreachable. Such a test must point spring.data.redis.* somewhere itself.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({PostgresContainerConfiguration.class, MutableClockConfiguration.class})
@ExtendWith(MutableClockResetExtension.class)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public @interface IntegrationTestWithoutRedis {
}
