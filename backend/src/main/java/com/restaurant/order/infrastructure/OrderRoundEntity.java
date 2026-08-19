package com.restaurant.order.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_rounds")
public class OrderRoundEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID orderId;
	private int roundNo;
	public UUID getId() { return id; }
	public UUID getOrderId() { return orderId; }
	public int getRoundNo() { return roundNo; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOrderId(UUID orderId) { this.orderId = orderId; }
	public void setRoundNo(int roundNo) { this.roundNo = roundNo; }
}
