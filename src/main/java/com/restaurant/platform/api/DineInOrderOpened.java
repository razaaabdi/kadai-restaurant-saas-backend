package com.restaurant.platform.api;
import java.util.UUID;
public record DineInOrderOpened(UUID tenantId, UUID outletId, UUID tableId, UUID orderId, UUID waiterId) {}
