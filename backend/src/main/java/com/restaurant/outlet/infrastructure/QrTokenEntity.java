package com.restaurant.outlet.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "qr_tokens")
public class QrTokenEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID tableId;
	private String tokenHash;
	private boolean active = true;
	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public UUID getTableId() { return tableId; }
	public void setTableId(UUID tableId) { this.tableId = tableId; }
	public String getTokenHash() { return tokenHash; }
	public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }
}
