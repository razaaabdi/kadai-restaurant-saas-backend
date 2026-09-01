package com.restaurant.platform.api;

import java.util.UUID;

public interface SubscriptionPolicy {
	void assertNewWorkAllowed(UUID tenantId);
}
