package com.restaurant.reporting.infrastructure;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class DailySalesId implements Serializable {
	private UUID tenantId;
	private UUID outletId;
	private LocalDate businessDate;
	public DailySalesId() {}
	public DailySalesId(UUID tenantId, UUID outletId, LocalDate businessDate) {
		this.tenantId = tenantId;
		this.outletId = outletId;
		this.businessDate = businessDate;
	}
	@Override public boolean equals(Object o) {
		if (!(o instanceof DailySalesId d)) return false;
		return Objects.equals(tenantId, d.tenantId) && Objects.equals(outletId, d.outletId)
				&& Objects.equals(businessDate, d.businessDate);
	}
	@Override public int hashCode() { return Objects.hash(tenantId, outletId, businessDate); }
}
