package com.restaurant.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLocationRepository extends JpaRepository<StockLocationEntity, UUID> {
	List<StockLocationEntity> findByOutletIdOrderByNameAsc(UUID outletId);
	Optional<StockLocationEntity> findFirstByOutletIdAndNameIgnoreCase(UUID outletId, String name);
	Optional<StockLocationEntity> findByIdAndOutletId(UUID id, UUID outletId);
	boolean existsByOutletIdAndNameIgnoreCase(UUID outletId, String name);
}
