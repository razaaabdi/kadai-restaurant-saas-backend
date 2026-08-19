package com.restaurant.kitchen.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KotRepository extends JpaRepository<KotEntity, UUID> {
	List<KotEntity> findByOrderId(UUID orderId);
	List<KotEntity> findByOutletIdAndStatus(UUID outletId, String status);
}
