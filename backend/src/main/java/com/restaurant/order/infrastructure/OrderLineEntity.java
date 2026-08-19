package com.restaurant.order.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

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
	public UUID getId() { return id; }
	public UUID getOrderId() { return orderId; }
	public UUID getRoundId() { return roundId; }
	public UUID getVariantId() { return variantId; }
	public String getNameSnapshot() { return nameSnapshot; }
	public BigDecimal getQty() { return qty; }
	public long getUnitPaise() { return unitPaise; }
	public long getLinePaise() { return linePaise; }
	public UUID getRecipeVersionId() { return recipeVersionId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOrderId(UUID orderId) { this.orderId = orderId; }
	public void setRoundId(UUID roundId) { this.roundId = roundId; }
	public void setVariantId(UUID variantId) { this.variantId = variantId; }
	public void setNameSnapshot(String nameSnapshot) { this.nameSnapshot = nameSnapshot; }
	public void setQty(BigDecimal qty) { this.qty = qty; }
	public void setUnitPaise(long unitPaise) { this.unitPaise = unitPaise; }
	public void setLinePaise(long linePaise) { this.linePaise = linePaise; }
	public void setRecipeVersionId(UUID recipeVersionId) { this.recipeVersionId = recipeVersionId; }
}
