package com.restaurant.inventory.domain;

public enum StockTransactionType {
	OPENING_STOCK,
	PURCHASE,
	SALE,
	SALE_CONSUMPTION,
	MANUAL_CONSUMPTION,
	WASTAGE,
	ADJUSTMENT_IN,
	ADJUSTMENT_OUT,
	VOID_REVERSAL,
	AUDIT_CORRECTION;

	public boolean inbound() {
		return this == OPENING_STOCK || this == PURCHASE || this == ADJUSTMENT_IN || this == VOID_REVERSAL
				|| this == AUDIT_CORRECTION;
	}
}
