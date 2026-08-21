package com.restaurant.platform.api;

import java.util.UUID;

public record KotStatusChanged(UUID tenantId, UUID orderId, UUID kotId, UUID roundId, String kotStatus) {}
