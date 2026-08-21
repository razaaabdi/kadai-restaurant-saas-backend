package com.restaurant.waiter.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaiterWorkProfileRepository extends JpaRepository<WaiterWorkProfileEntity, UUID> {
	Optional<WaiterWorkProfileEntity> findByOutletIdAndWaiterId(UUID outletId, UUID waiterId);
	List<WaiterWorkProfileEntity> findByOutletId(UUID outletId);
}
