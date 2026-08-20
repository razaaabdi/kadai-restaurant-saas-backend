package com.restaurant.organization.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "plans")
public class PlanEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private String code;
	private boolean inventoryEnabled = true;
	private boolean multiOutlet = true;
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setCode(String code) { this.code = code; }
	public boolean isInventoryEnabled() { return inventoryEnabled; }
	public void setInventoryEnabled(boolean inventoryEnabled) { this.inventoryEnabled = inventoryEnabled; }
}
