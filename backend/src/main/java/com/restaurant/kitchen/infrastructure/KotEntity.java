package com.restaurant.kitchen.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "kots")
public class KotEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private UUID orderId;
	private UUID roundId;
	private String station = "HOT";
	private int kotNumber;
	private String status = "NEW";
	private UUID reprintOf;

	public UUID getId() { return id; }
	public UUID getOrderId() { return orderId; }
	public UUID getRoundId() { return roundId; }
	public int getKotNumber() { return kotNumber; }
	public String getStatus() { return status; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public void setOrderId(UUID orderId) { this.orderId = orderId; }
	public void setRoundId(UUID roundId) { this.roundId = roundId; }
	public void setKotNumber(int kotNumber) { this.kotNumber = kotNumber; }
	public void setStatus(String status) { this.status = status; }
}
