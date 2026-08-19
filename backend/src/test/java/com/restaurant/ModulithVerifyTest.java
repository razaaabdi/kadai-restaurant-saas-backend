package com.restaurant;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithVerifyTest {
	@Test
	void modules() {
		ApplicationModules.of(RestaurantSaasApplication.class).verify();
	}
}
