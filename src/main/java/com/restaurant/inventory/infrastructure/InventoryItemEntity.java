package com.restaurant.inventory.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItemEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private String name;
	private String unit = "g";
	private String sku;
	private UUID categoryId;
	private BigDecimal minimumStock = BigDecimal.ZERO;
	private BigDecimal reorderLevel = BigDecimal.ZERO;
	private boolean active = true;
	@Version private long version;
	private Instant createdAt = Instant.now();
	private Instant updatedAt = Instant.now();

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public UUID getOutletId() { return outletId; }
	public String getName() { return name; }
	public String getUnit() { return unit; }
	public String getSku() { return sku; }
	public UUID getCategoryId() { return categoryId; }
	public BigDecimal getMinimumStock() { return minimumStock; }
	public BigDecimal getReorderLevel() { return reorderLevel; }
	public boolean isActive() { return active; }
	public long getVersion() { return version; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public void setName(String name) { this.name = name; }
	public void setUnit(String unit) { this.unit = unit; }
	public void setSku(String sku) { this.sku = sku; }
	public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
	public void setMinimumStock(BigDecimal minimumStock) { this.minimumStock = minimumStock; }
	public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }
	public void setActive(boolean active) { this.active = active; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
