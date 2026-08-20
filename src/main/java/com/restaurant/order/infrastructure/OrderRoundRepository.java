package com.restaurant.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRoundRepository extends JpaRepository<OrderRoundEntity, UUID> {
	List<OrderRoundEntity> findByOrderIdOrderByRoundNo(UUID orderId);
}
