package com.restaurant.catalog.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface TaxCodeRepository extends JpaRepository<TaxCodeEntity, UUID> {
  List<TaxCodeEntity> findByTenantId(UUID tenantId);
}
