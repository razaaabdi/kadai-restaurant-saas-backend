package com.restaurant.platform.api;

import java.util.UUID;

public record KotStatusChanged(UUID tenantId, UUID orderId, String kotStatus) {}
