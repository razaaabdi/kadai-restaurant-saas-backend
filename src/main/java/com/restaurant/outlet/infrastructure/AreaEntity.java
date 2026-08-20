package com.restaurant.outlet.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "areas")
public class AreaEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private String name;
	public UUID getId() { return id; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public void setName(String name) { this.name = name; }
	public UUID getOutletId() { return outletId; }
	public String getName() { return name; }
}
