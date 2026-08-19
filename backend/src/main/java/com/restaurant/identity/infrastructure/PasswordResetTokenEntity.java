package com.restaurant.identity.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID userId;
	private String tokenHash;
	private Instant expiresAt;
	private boolean used;
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setUserId(UUID userId) { this.userId = userId; }
	public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
	public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
	public boolean isUsed() { return used; }
	public void setUsed(boolean used) { this.used = used; }
	public UUID getUserId() { return userId; }
	public Instant getExpiresAt() { return expiresAt; }
}
