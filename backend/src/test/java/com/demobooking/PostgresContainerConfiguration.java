package com.demobooking;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// One container per JVM, not per test context: every distinct context (spy beans, dedicated
// property sets) would otherwise start its own. Spring caches contexts until the JVM exits, so
// none closes it mid-run, and Ryuk removes it afterwards. Sharing the database across contexts
// is already true within one cached context, so tests must not assume an empty table anyway.
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfiguration {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES;
	}

}
