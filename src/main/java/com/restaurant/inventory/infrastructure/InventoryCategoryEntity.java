package com.restaurant.inventory.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_categories")
public class InventoryCategoryEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private String name;
	private String description = "";
	private boolean active = true;
	private Instant createdAt = Instant.now();
	private Instant updatedAt = Instant.now();

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public boolean isActive() { return active; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setName(String name) { this.name = name; }
	public void setDescription(String description) { this.description = description; }
	public void setActive(boolean active) { this.active = active; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
