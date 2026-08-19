package com.restaurant.platform.infrastructure;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID actorId;
	private String action;
	private String entityType;
	private UUID entityId;
	@Column(columnDefinition = "text") private String detail;
	private Instant createdAt = Instant.now();
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setActorId(UUID actorId) { this.actorId = actorId; }
	public void setAction(String action) { this.action = action; }
	public void setEntityType(String entityType) { this.entityType = entityType; }
	public void setEntityId(UUID entityId) { this.entityId = entityId; }
	public void setDetail(String detail) { this.detail = detail; }
}
