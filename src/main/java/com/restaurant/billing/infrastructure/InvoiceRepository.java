package com.restaurant.billing.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {
	java.util.Optional<InvoiceEntity> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(UUID orderId, String status);
	Optional<InvoiceEntity> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
	@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from InvoiceEntity i where i.id=:id") Optional<InvoiceEntity> findLockedById(@Param("id") UUID id);
}
