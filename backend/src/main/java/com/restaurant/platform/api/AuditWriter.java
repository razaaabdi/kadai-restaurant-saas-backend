package com.restaurant.platform.api;
import com.restaurant.platform.infrastructure.AuditLogEntity;
import com.restaurant.platform.infrastructure.AuditLogRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class AuditWriter {
	private final AuditLogRepository repo;
	public AuditWriter(AuditLogRepository repo) { this.repo = repo; }
	public void write(String action, String entityType, UUID entityId, String detail) {
		AuditLogEntity e = new AuditLogEntity();
		var p = TenantContext.get();
		if (p != null) {
			e.setTenantId(p.tenantId());
			e.setActorId(p.userId());
		}
		e.setAction(action);
		e.setEntityType(entityType);
		e.setEntityId(entityId);
		e.setDetail(detail);
		repo.save(e);
	}
}
