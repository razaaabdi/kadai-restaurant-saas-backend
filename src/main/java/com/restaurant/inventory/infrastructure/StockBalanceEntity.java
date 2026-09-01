package com.restaurant.inventory.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_balances")
public class StockBalanceEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private UUID stockLocationId;
	private UUID inventoryItemId;
	private BigDecimal qty = BigDecimal.ZERO;
	private long averageCostPaise;
	private long inventoryValuePaise;
	@Version private Long version;
	private Instant updatedAt = Instant.now();

	public UUID getId() { return id; }
	public UUID getOutletId() { return outletId; }
	public UUID getStockLocationId() { return stockLocationId; }
	public UUID getInventoryItemId() { return inventoryItemId; }
	public BigDecimal getQty() { return qty; }
	public long getAverageCostPaise() { return averageCostPaise; }
	public long getInventoryValuePaise() { return inventoryValuePaise; }
	public long getVersion() { return version == null ? 0 : version; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setQty(BigDecimal qty) { this.qty = qty; }
	public void setAverageCostPaise(long averageCostPaise) { this.averageCostPaise = averageCostPaise; }
	public void setInventoryValuePaise(long inventoryValuePaise) { this.inventoryValuePaise = inventoryValuePaise; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public void setStockLocationId(UUID stockLocationId) { this.stockLocationId = stockLocationId; }
	public void setInventoryItemId(UUID inventoryItemId) { this.inventoryItemId = inventoryItemId; }
}
