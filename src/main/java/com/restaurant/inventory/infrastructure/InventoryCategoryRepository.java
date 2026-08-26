package com.restaurant.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCategoryRepository extends JpaRepository<InventoryCategoryEntity, UUID> {
	List<InventoryCategoryEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
	boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
	Optional<InventoryCategoryEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
