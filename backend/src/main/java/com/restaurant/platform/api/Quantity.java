package com.restaurant.platform.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Quantity(BigDecimal value) {
	public Quantity {
		value = value.setScale(4, RoundingMode.HALF_UP);
	}
	public static Quantity of(String v) { return new Quantity(new BigDecimal(v)); }
	public Quantity plus(Quantity o) { return new Quantity(value.add(o.value)); }
	public Quantity negate() { return new Quantity(value.negate()); }
}
