package com.restaurant.catalog.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface ItemRepository extends JpaRepository<ItemEntity, UUID> {
  List<ItemEntity> findByTenantId(UUID tenantId);
  List<ItemEntity> findByOutletIdAndDeletedFalse(UUID outletId);
}
