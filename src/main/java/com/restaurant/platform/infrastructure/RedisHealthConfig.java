package com.restaurant.platform.infrastructure;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class RedisHealthConfig {

	/** Windows Redis INFO includes paths like C:\Users\..., which breaks Spring's default INFO parser. */
	@Bean(name = "redisHealthIndicator")
	HealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
		return () -> {
			try (var connection = connectionFactory.getConnection()) {
				String pong = connection.ping();
				if (pong == null || !pong.equalsIgnoreCase("PONG")) {
					return Health.down().withDetail("ping", pong).build();
				}
				return Health.up().build();
			} catch (Exception ex) {
				return Health.down(ex).build();
			}
		};
	}
}
