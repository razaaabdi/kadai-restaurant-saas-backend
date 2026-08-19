package com.restaurant.platform.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyId.class)
public class IdempotencyEntity {
	@Id private UUID tenantId;
	@Id @Column(name = "key") private String key;
	private String requestHash;
	private int statusCode;
	@Column(columnDefinition = "text") private String responseBody;
	private Instant createdAt = Instant.now();
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public String getKey() { return key; }
	public void setKey(String key) { this.key = key; }
	public String getRequestHash() { return requestHash; }
	public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
	public int getStatusCode() { return statusCode; }
	public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
	public String getResponseBody() { return responseBody; }
	public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
}
