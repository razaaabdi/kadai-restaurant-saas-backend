package com.restaurant.order.domain;

import com.restaurant.platform.api.ApiException;

public enum OrderType {
	DINE_IN,
	TAKEAWAY;

	public static OrderType parse(String value) {
		try {
			return value == null ? null : valueOf(value.strip().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw ApiException.bad("ORDER_TYPE", "Order type must be DINE_IN or TAKEAWAY");
		}
	}
}
