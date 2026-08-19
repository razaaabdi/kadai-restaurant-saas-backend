package com.restaurant.order.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_line_modifiers")
public class OrderLineModifierEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID orderLineId;
	private UUID modifierId;
	private String nameSnapshot;
	private long extraPaise;
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOrderLineId(UUID orderLineId) { this.orderLineId = orderLineId; }
	public void setModifierId(UUID modifierId) { this.modifierId = modifierId; }
	public void setNameSnapshot(String nameSnapshot) { this.nameSnapshot = nameSnapshot; }
	public void setExtraPaise(long extraPaise) { this.extraPaise = extraPaise; }
	public long getExtraPaise() { return extraPaise; }
}
