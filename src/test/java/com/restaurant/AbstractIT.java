package com.restaurant;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIT {
	static final PostgreSQLContainer postgres;
	static final GenericContainer<?> redis;

	static {
		postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
				.withInitScript("pg-init.sql");
		postgres.start();
		redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
		redis.start();
	}

	@LocalServerPort
	protected int port;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", postgres::getJdbcUrl);
		r.add("spring.datasource.username", () -> "restaurant_app");
		r.add("spring.datasource.password", () -> "app_secret");
		r.add("spring.flyway.user", postgres::getUsername);
		r.add("spring.flyway.password", postgres::getPassword);
		r.add("spring.flyway.url", postgres::getJdbcUrl);
		r.add("spring.data.redis.host", redis::getHost);
		r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
		r.add("app.jwt.secret", () -> "test-secret-must-be-32-bytes-min!");
		r.add("app.outbox.poller", () -> "false");
		r.add("app.legacy-onboarding-enabled", () -> "true");
		r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	protected String url(String path) {
		return "http://localhost:" + port + path;
	}
}
