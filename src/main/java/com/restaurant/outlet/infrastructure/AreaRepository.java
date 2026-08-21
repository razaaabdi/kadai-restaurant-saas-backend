package com.restaurant.outlet.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface AreaRepository extends JpaRepository<AreaEntity, UUID> {
	List<AreaEntity> findByOutletId(UUID outletId);
	boolean existsByOutletIdAndNameIgnoreCase(UUID outletId, String name);
}
