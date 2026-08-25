package com.restaurant.inventory.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stock_transactions")
public class StockTransactionEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private UUID stockLocationId;
	private UUID inventoryItemId;
	private String type;
	private BigDecimal qty;
	private UUID orderId;
	private Instant createdAt = Instant.now();
	private String unit;
	private long unitCostPaise;
	private long totalCostPaise;
	private String referenceType;
	private UUID referenceId;
	private String reason;
	private String notes;
	private UUID performedBy;
	private LocalDate businessDate;

	public UUID getId() { return id; }
	public UUID getOutletId() { return outletId; }
	public UUID getStockLocationId() { return stockLocationId; }
	public UUID getInventoryItemId() { return inventoryItemId; }
	public String getType() { return type; }
	public BigDecimal getQty() { return qty; }
	public UUID getOrderId() { return orderId; }
	public Instant getCreatedAt() { return createdAt; }
	public String getUnit() { return unit; }
	public long getUnitCostPaise() { return unitCostPaise; }
	public long getTotalCostPaise() { return totalCostPaise; }
	public String getReferenceType() { return referenceType; }
	public UUID getReferenceId() { return referenceId; }
	public String getReason() { return reason; }
	public String getNotes() { return notes; }
	public UUID getPerformedBy() { return performedBy; }
	public LocalDate getBusinessDate() { return businessDate; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public void setStockLocationId(UUID stockLocationId) { this.stockLocationId = stockLocationId; }
	public void setInventoryItemId(UUID inventoryItemId) { this.inventoryItemId = inventoryItemId; }
	public void setType(String type) { this.type = type; }
	public void setQty(BigDecimal qty) { this.qty = qty; }
	public void setOrderId(UUID orderId) { this.orderId = orderId; }
	public void setUnit(String unit) { this.unit = unit; }
	public void setUnitCostPaise(long unitCostPaise) { this.unitCostPaise = unitCostPaise; }
	public void setTotalCostPaise(long totalCostPaise) { this.totalCostPaise = totalCostPaise; }
	public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
	public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }
	public void setReason(String reason) { this.reason = reason; }
	public void setNotes(String notes) { this.notes = notes; }
	public void setPerformedBy(UUID performedBy) { this.performedBy = performedBy; }
	public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
}
