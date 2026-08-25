package com.restaurant.inventory.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "stock_locations")
public class StockLocationEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private String name;
	private String type = "MAIN_STORE";
	private boolean active = true;

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public UUID getOutletId() { return outletId; }
	public String getName() { return name; }
	public String getType() { return type; }
	public boolean isActive() { return active; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public void setName(String name) { this.name = name; }
	public void setType(String type) { this.type = type; }
	public void setActive(boolean active) { this.active = active; }
}
