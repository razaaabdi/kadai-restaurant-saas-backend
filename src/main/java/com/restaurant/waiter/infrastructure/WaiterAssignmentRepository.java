package com.restaurant.waiter.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaiterAssignmentRepository extends JpaRepository<WaiterAssignmentEntity, UUID> {
	List<WaiterAssignmentEntity> findByOutletIdOrderByAssignedAtDesc(UUID outletId);
	List<WaiterAssignmentEntity> findByOutletIdAndWaiterIdOrderByAssignedAtDesc(UUID outletId, UUID waiterId);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<WaiterAssignmentEntity> findFirstByOrderIdAndStatusIn(UUID orderId, List<String> statuses);
	long countByOutletIdAndWaiterIdAndStatusIn(UUID outletId, UUID waiterId, List<String> statuses);
}
