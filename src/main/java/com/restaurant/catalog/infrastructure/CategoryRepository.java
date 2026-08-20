package com.restaurant.catalog.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {
  List<CategoryEntity> findByTenantId(UUID tenantId);
}
