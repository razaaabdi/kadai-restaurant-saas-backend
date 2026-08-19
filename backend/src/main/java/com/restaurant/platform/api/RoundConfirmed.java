package com.restaurant.platform.api;

import java.util.UUID;

public record RoundConfirmed(UUID tenantId, UUID outletId, UUID orderId, UUID roundId) {}
