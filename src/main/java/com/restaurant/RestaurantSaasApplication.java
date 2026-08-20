package com.restaurant;

import com.restaurant.platform.api.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class RestaurantSaasApplication {
	public static void main(String[] args) {
		SpringApplication.run(RestaurantSaasApplication.class, args);
	}
}
