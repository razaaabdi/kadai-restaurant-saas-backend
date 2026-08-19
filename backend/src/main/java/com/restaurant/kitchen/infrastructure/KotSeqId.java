package com.restaurant.kitchen.infrastructure;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class KotSeqId implements Serializable {
	private UUID tenantId;
	private UUID outletId;
	public KotSeqId() {}
	public KotSeqId(UUID tenantId, UUID outletId) {
		this.tenantId = tenantId;
		this.outletId = outletId;
	}
	@Override public boolean equals(Object o) {
		if (!(o instanceof KotSeqId k)) return false;
		return Objects.equals(tenantId, k.tenantId) && Objects.equals(outletId, k.outletId);
	}
	@Override public int hashCode() { return Objects.hash(tenantId, outletId); }
}
