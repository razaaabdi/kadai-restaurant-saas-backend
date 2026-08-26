package com.restaurant.inventory.domain;

import java.math.BigDecimal;

public enum StockStatus {
	IN_STOCK, LOW_STOCK, OUT_OF_STOCK;

	public static StockStatus of(BigDecimal qty, BigDecimal reorderLevel) {
		if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return OUT_OF_STOCK;
		if (reorderLevel != null && qty.compareTo(reorderLevel) <= 0) return LOW_STOCK;
		return IN_STOCK;
	}
}
