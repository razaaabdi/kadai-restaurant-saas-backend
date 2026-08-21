package com.restaurant.platform.api;
import java.util.UUID;
public record OrderClosed(UUID tenantId,UUID outletId,UUID orderId,UUID tableId){}
