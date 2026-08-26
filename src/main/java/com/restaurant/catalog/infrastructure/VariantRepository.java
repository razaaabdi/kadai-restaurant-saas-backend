package com.restaurant.catalog.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection; import java.util.List; import java.util.UUID;
public interface VariantRepository extends JpaRepository<VariantEntity, UUID> {
  List<VariantEntity> findByTenantId(UUID tenantId);
  List<VariantEntity> findByItemId(UUID itemId);
  List<VariantEntity> findByItemIdIn(Collection<UUID> itemIds);
}
