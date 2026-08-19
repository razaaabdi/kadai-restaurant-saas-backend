package com.restaurant.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
	List<OrderEntity> findByOutletIdAndTableId(UUID outletId, UUID tableId);
	Optional<OrderEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
