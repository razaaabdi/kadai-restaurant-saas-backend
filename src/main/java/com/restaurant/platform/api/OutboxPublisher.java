package com.restaurant.platform.api;
import com.restaurant.platform.infrastructure.OutboxEntity;
import com.restaurant.platform.infrastructure.OutboxRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class OutboxPublisher {
	private final OutboxRepository repo;
	public OutboxPublisher(OutboxRepository repo) { this.repo = repo; }
	public void publish(UUID tenantId, String type, String payload) {
		OutboxEntity e = new OutboxEntity();
		e.setTenantId(tenantId);
		e.setType(type);
		e.setPayload(payload);
		repo.save(e);
	}
}
