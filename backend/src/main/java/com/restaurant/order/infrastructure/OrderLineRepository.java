package com.restaurant.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderLineRepository extends JpaRepository<OrderLineEntity, UUID> {
	List<OrderLineEntity> findByOrderId(UUID orderId);
}
