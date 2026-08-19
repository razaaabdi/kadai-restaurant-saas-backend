package com.restaurant.outlet.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface QrTokenRepository extends JpaRepository<QrTokenEntity, UUID> {
	List<QrTokenEntity> findByTableIdAndActiveTrue(UUID tableId);
	Optional<QrTokenEntity> findByIdAndActiveTrue(UUID id);
}
