package com.restaurant.outlet.api;

import java.util.UUID;

public record QrLookup(
		UUID tokenId,
		UUID tenantId,
		UUID tableId,
		UUID outletId,
		boolean active,
		String tableStatus,
		boolean qrLocked,
		boolean qrOrderingEnabled,
		String outletName,
		String tableCode
) {}
