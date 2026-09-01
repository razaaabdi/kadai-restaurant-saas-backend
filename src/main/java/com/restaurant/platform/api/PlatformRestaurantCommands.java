package com.restaurant.platform.api;

import java.util.Map;
import java.util.UUID;

public interface PlatformRestaurantCommands {
	Map<String, Object> create(UUID actorId, Map<String, Object> request);
	void changeTenantStatus(UUID actorId, UUID tenantId, String action, String reason, long expectedVersion);
	Map<String, Object> addOutlet(UUID actorId, UUID tenantId, Map<String, Object> request);
	Map<String, Object> changeOutletStatus(UUID actorId, UUID outletId, String action, String reason, long expectedVersion);
	void renew(UUID actorId, UUID tenantId, int months, String reason, long expectedVersion);
	void changePlan(UUID actorId, UUID tenantId, UUID planId, String reason, long expectedVersion);
}
