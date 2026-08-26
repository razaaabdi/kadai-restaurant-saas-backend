package com.restaurant.configuration.api;

import java.util.Map;

public record OrderConfigurationResponse(
		Map<String, Object> configuration,
		long tenantVersion,
		Long outletVersion) {
}
