package com.restaurant.catalog.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface ModifierRepository extends JpaRepository<ModifierEntity, UUID> {
  List<ModifierEntity> findByTenantId(UUID tenantId);
}
