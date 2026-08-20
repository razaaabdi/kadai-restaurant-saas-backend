package com.restaurant.identity.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
@IdClass(UserRoleId.class)
public class UserRoleEntity {
	private UUID tenantId;
	@Id private UUID userId;
	@Id private UUID roleId;
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setUserId(UUID userId) { this.userId = userId; }
	public UUID getRoleId() { return roleId; }
	public void setRoleId(UUID roleId) { this.roleId = roleId; }
	public UUID getUserId() { return userId; }
}

class UserRoleId implements Serializable {
	private UUID userId;
	private UUID roleId;
	@Override public boolean equals(Object o) {
		if (!(o instanceof UserRoleId u)) return false;
		return Objects.equals(userId, u.userId) && Objects.equals(roleId, u.roleId);
	}
	@Override public int hashCode() { return Objects.hash(userId, roleId); }
}
