package com.restaurant.waiter.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WaiterNotificationRepository extends JpaRepository<WaiterNotificationEntity, UUID> {
	List<WaiterNotificationEntity> findByOutletIdOrderByCreatedAtDesc(UUID outletId);
	boolean existsByDedupeKey(String dedupeKey);
}
