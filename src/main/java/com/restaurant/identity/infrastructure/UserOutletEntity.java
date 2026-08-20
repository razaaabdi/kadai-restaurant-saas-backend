package com.restaurant.identity.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_outlets")
@IdClass(UserOutletId.class)
public class UserOutletEntity {
	private UUID tenantId;
	@Id private UUID userId;
	@Id private UUID outletId;
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setUserId(UUID userId) { this.userId = userId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public UUID getOutletId() { return outletId; }
}

class UserOutletId implements Serializable {
	private UUID userId;
	private UUID outletId;
	@Override public boolean equals(Object o) {
		if (!(o instanceof UserOutletId u)) return false;
		return Objects.equals(userId, u.userId) && Objects.equals(outletId, u.outletId);
	}
	@Override public int hashCode() { return Objects.hash(userId, outletId); }
}
