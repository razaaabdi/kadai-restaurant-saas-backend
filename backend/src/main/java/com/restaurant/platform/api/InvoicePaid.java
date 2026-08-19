package com.restaurant.platform.api;

import java.util.UUID;

public record InvoicePaid(UUID tenantId, UUID orderId, UUID tableId) {}
