package com.restaurant.inventory.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, UUID> {
	List<InventoryItemEntity> findByOutletIdOrderByNameAsc(UUID outletId);
	Page<InventoryItemEntity> findByOutletId(UUID outletId, Pageable pageable);
	Page<InventoryItemEntity> findByOutletIdAndNameContainingIgnoreCase(UUID outletId, String name, Pageable pageable);
	boolean existsByTenantIdAndSkuIgnoreCase(UUID tenantId, String sku);
}
