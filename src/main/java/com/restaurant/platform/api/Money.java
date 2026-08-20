package com.restaurant.platform.api;

/** Paise only — never float/double. */
public record Money(long paise) {
	public static Money zero() { return new Money(0); }
	public Money plus(Money o) { return new Money(paise + o.paise); }
	public Money minus(Money o) { return new Money(paise - o.paise); }
	public Money times(java.math.BigDecimal qty) {
		return new Money(qty.multiply(java.math.BigDecimal.valueOf(paise)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact());
	}
}
