package com.restaurant.inventory.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface StockTransactionRepository extends JpaRepository<StockTransactionEntity, UUID> {
  List<StockTransactionEntity> findByOutletIdAndInventoryItemId(UUID outletId, UUID inventoryItemId);
  List<StockTransactionEntity> findByOrderId(UUID orderId);
}
