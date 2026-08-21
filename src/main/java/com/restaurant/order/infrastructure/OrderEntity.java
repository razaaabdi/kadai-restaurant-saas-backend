package com.restaurant.order.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private UUID tableId;
	private String channel;
	private String status;
	private UUID assignedWaiterId;
	private int guestCount = 1;
	private boolean guestFrozen;
	private long subtotalPaise;
	private long discountPaise;
	private long serviceChargePaise;
	private long packagingPaise;
	private long taxPaise;
	private long totalPaise;
	private Instant createdAt = Instant.now();

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public UUID getOutletId() { return outletId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public UUID getTableId() { return tableId; }
	public void setTableId(UUID tableId) { this.tableId = tableId; }
	public String getChannel() { return channel; }
	public void setChannel(String channel) { this.channel = channel; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public UUID getAssignedWaiterId() { return assignedWaiterId; }
	public void setAssignedWaiterId(UUID assignedWaiterId) { this.assignedWaiterId = assignedWaiterId; }
	public int getGuestCount() { return guestCount; }
	public void setGuestCount(int guestCount) { this.guestCount = guestCount; }
	public Instant getCreatedAt() { return createdAt; }
	public boolean isGuestFrozen() { return guestFrozen; }
	public void setGuestFrozen(boolean guestFrozen) { this.guestFrozen = guestFrozen; }
	public long getSubtotalPaise() { return subtotalPaise; }
	public void setSubtotalPaise(long subtotalPaise) { this.subtotalPaise = subtotalPaise; }
	public long getDiscountPaise() { return discountPaise; }
	public void setDiscountPaise(long discountPaise) { this.discountPaise = discountPaise; }
	public long getServiceChargePaise() { return serviceChargePaise; }
	public void setServiceChargePaise(long serviceChargePaise) { this.serviceChargePaise = serviceChargePaise; }
	public long getPackagingPaise() { return packagingPaise; }
	public void setPackagingPaise(long packagingPaise) { this.packagingPaise = packagingPaise; }
	public long getTaxPaise() { return taxPaise; }
	public void setTaxPaise(long taxPaise) { this.taxPaise = taxPaise; }
	public long getTotalPaise() { return totalPaise; }
	public void setTotalPaise(long totalPaise) { this.totalPaise = totalPaise; }
}
