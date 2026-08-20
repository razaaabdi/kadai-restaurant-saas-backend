package com.restaurant.platform.infrastructure;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "outbox")
public class OutboxEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private String type;
	@Column(columnDefinition = "text") private String payload;
	private String status = "PENDING";
	private int retryCount;
	private Instant nextAttemptAt = Instant.now();
	private String lastError;
	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public String getType() { return type; }
	public void setType(String type) { this.type = type; }
	public String getPayload() { return payload; }
	public void setPayload(String payload) { this.payload = payload; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public int getRetryCount() { return retryCount; }
	public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
	public Instant getNextAttemptAt() { return nextAttemptAt; }
	public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
	public void setLastError(String lastError) { this.lastError = lastError; }
}
