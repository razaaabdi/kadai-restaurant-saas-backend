package com.restaurant.inventory.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.UUID;
public interface StockBalanceRepository extends JpaRepository<StockBalanceEntity, UUID> {
  Optional<StockBalanceEntity> findByOutletIdAndInventoryItemId(UUID outletId, UUID inventoryItemId);
}
