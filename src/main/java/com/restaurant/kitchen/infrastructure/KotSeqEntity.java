package com.restaurant.kitchen.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "kot_seq")
@IdClass(KotSeqId.class)
public class KotSeqEntity {
	@Id private UUID tenantId;
	@Id private UUID outletId;
	private int lastNumber;
	public int getLastNumber() { return lastNumber; }
	public void setLastNumber(int lastNumber) { this.lastNumber = lastNumber; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
}
