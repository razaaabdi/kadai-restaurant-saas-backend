package com.restaurant.billing.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "invoices")
public class InvoiceEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private UUID orderId;
	private String status = "OPEN";
	private long subtotalPaise;
	private long discountPaise;
	private long serviceChargePaise;
	private long packagingPaise;
	private long taxPaise;
	private long roundingPaise;
	private long totalPaise;
	private boolean taxInclusive;
	private String roundingMode = "HALF_UP";
	@Version private long version;
	public UUID getId() { return id; }
	public UUID getOrderId() { return orderId; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public long getTotalPaise() { return totalPaise; }
	public long getSubtotalPaise() { return subtotalPaise; }
	public long getDiscountPaise() { return discountPaise; }
	public long getTaxPaise() { return taxPaise; }
	public UUID getOutletId() { return outletId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public void setOrderId(UUID orderId) { this.orderId = orderId; }
	public void setSubtotalPaise(long subtotalPaise) { this.subtotalPaise = subtotalPaise; }
	public void setDiscountPaise(long discountPaise) { this.discountPaise = discountPaise; }
	public void setServiceChargePaise(long serviceChargePaise) { this.serviceChargePaise = serviceChargePaise; }
	public void setPackagingPaise(long packagingPaise) { this.packagingPaise = packagingPaise; }
	public void setTaxPaise(long taxPaise) { this.taxPaise = taxPaise; }
	public void setRoundingPaise(long roundingPaise) { this.roundingPaise = roundingPaise; }
	public void setTotalPaise(long totalPaise) { this.totalPaise = totalPaise; }
	public void setTaxInclusive(boolean taxInclusive) { this.taxInclusive = taxInclusive; }
	public void setRoundingMode(String roundingMode) { this.roundingMode = roundingMode; }
	public UUID getTenantId() { return tenantId; }
}
