package com.restaurant.platform.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record TenantPrincipal(
		UUID tenantId,
		UUID userId,
		List<UUID> outletIds,
		Set<String> roles,
		String typ,
		UUID tableId,
		UUID sessionId,
		UUID qrTokenId,
		UUID outletId
) {
	public boolean isGuest() { return "table_guest".equals(typ); }
	public boolean hasRole(String r) { return roles != null && roles.contains(r); }
}
