package com.restaurant.order.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Version;

@Entity
@Table(name = "order_lines")
public class OrderLineEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID orderId;
	private UUID roundId;
	private UUID variantId;
	private String nameSnapshot;
	private BigDecimal qty;
	private long unitPaise;
	private long linePaise;
	private UUID recipeVersionId;
	private String fulfilmentStatus = "SENT_TO_KITCHEN";
	private String notes;
	private UUID pickedUpBy;
	private Instant pickedUpAt;
	private UUID servedBy;
	private Instant servedAt;
	@Version private Long version;
	public UUID getId() { return id; }
	public UUID getOrderId() { return orderId; }
	public UUID getRoundId() { return roundId; }
	public UUID getVariantId() { return variantId; }
	public String getNameSnapshot() { return nameSnapshot; }
	public BigDecimal getQty() { return qty; }
	public long getUnitPaise() { return unitPaise; }
	public long getLinePaise() { return linePaise; }
	public UUID getRecipeVersionId() { return recipeVersionId; }
	public String getFulfilmentStatus() { return fulfilmentStatus; }
	public String getNotes() { return notes; }
	public UUID getPickedUpBy() { return pickedUpBy; }
	public Instant getPickedUpAt() { return pickedUpAt; }
	public UUID getServedBy() { return servedBy; }
	public Instant getServedAt() { return servedAt; }
	public long getVersion() { return version == null ? 0 : version; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOrderId(UUID orderId) { this.orderId = orderId; }
	public void setRoundId(UUID roundId) { this.roundId = roundId; }
	public void setVariantId(UUID variantId) { this.variantId = variantId; }
	public void setNameSnapshot(String nameSnapshot) { this.nameSnapshot = nameSnapshot; }
	public void setQty(BigDecimal qty) { this.qty = qty; }
	public void setUnitPaise(long unitPaise) { this.unitPaise = unitPaise; }
	public void setLinePaise(long linePaise) { this.linePaise = linePaise; }
	public void setRecipeVersionId(UUID recipeVersionId) { this.recipeVersionId = recipeVersionId; }
	public void setFulfilmentStatus(String fulfilmentStatus) { this.fulfilmentStatus = fulfilmentStatus; }
	public void setNotes(String notes) { this.notes = notes; }
	public void setPickedUpBy(UUID pickedUpBy) { this.pickedUpBy = pickedUpBy; }
	public void setPickedUpAt(Instant pickedUpAt) { this.pickedUpAt = pickedUpAt; }
	public void setServedBy(UUID servedBy) { this.servedBy = servedBy; }
	public void setServedAt(Instant servedAt) { this.servedAt = servedAt; }
}
