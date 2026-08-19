package com.restaurant.platform.infrastructure;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
public class IdempotencyId implements Serializable {
	private UUID tenantId;
	private String key;
	public IdempotencyId() {}
	public IdempotencyId(UUID tenantId, String key) { this.tenantId = tenantId; this.key = key; }
	@Override public boolean equals(Object o) {
		if (!(o instanceof IdempotencyId i)) return false;
		return Objects.equals(tenantId, i.tenantId) && Objects.equals(key, i.key);
	}
	@Override public int hashCode() { return Objects.hash(tenantId, key); }
}
