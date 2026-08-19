package com.restaurant.organization.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "brands")
public class BrandEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private String name;
	public UUID getId() { return id; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setName(String name) { this.name = name; }
}
