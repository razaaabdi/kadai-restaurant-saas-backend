package com.restaurant.identity.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "roles")
public class RoleEntity {
	@Id
	private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private String code;
	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
}
