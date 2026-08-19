package com.restaurant.outlet.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "table_sessions")
public class TableSessionEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private UUID tableId;
	private UUID qrTokenId;
	private Instant expiresAt;
	public UUID getId() { return id; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public UUID getOutletId() { return outletId; }
	public UUID getTableId() { return tableId; }
	public void setTableId(UUID tableId) { this.tableId = tableId; }
	public UUID getQrTokenId() { return qrTokenId; }
	public void setQrTokenId(UUID qrTokenId) { this.qrTokenId = qrTokenId; }
	public Instant getExpiresAt() { return expiresAt; }
	public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
