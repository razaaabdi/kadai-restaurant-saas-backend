package com.restaurant.inventory.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StockTransactionRepository extends JpaRepository<StockTransactionEntity, UUID> {
	List<StockTransactionEntity> findByOutletIdAndInventoryItemId(UUID outletId, UUID inventoryItemId);
	List<StockTransactionEntity> findByOutletIdAndInventoryItemIdOrderByCreatedAtAsc(UUID outletId, UUID inventoryItemId);
	List<StockTransactionEntity> findByOrderId(UUID orderId);
	Page<StockTransactionEntity> findByOutletIdAndInventoryItemIdOrderByCreatedAtDesc(UUID outletId, UUID inventoryItemId, Pageable pageable);
	Page<StockTransactionEntity> findByOutletIdOrderByCreatedAtDesc(UUID outletId, Pageable pageable);
	List<StockTransactionEntity> findByOutletIdAndTypeAndCreatedAtGreaterThanEqual(UUID outletId, String type, Instant from);
	boolean existsByInventoryItemId(UUID inventoryItemId);
}
