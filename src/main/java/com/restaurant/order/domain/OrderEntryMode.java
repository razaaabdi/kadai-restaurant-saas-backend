package com.restaurant.order.domain;

import com.restaurant.platform.api.ApiException;

public enum OrderEntryMode {
	DIRECT_POS,
	WAITER_PAPER_COUNTER;

	public static OrderEntryMode parse(String value) {
		try {
			return value == null ? null : valueOf(value.strip().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw ApiException.bad("ORDER_ENTRY_MODE", "Order entry mode must be DIRECT_POS or WAITER_PAPER_COUNTER");
		}
	}
}
