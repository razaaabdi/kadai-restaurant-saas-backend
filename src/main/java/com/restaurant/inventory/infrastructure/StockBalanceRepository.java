package com.restaurant.inventory.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceRepository extends JpaRepository<StockBalanceEntity, UUID> {
	List<StockBalanceEntity> findAllByOutletIdAndInventoryItemId(UUID outletId, UUID inventoryItemId);
		List<StockBalanceEntity> findByOutletId(UUID outletId);

		@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from StockBalanceEntity b where b.outletId = :outletId and b.stockLocationId = :locationId and b.inventoryItemId = :itemId")
	Optional<StockBalanceEntity> lockByOutletLocationItem(@Param("outletId") UUID outletId,
			@Param("locationId") UUID locationId, @Param("itemId") UUID itemId);
}
