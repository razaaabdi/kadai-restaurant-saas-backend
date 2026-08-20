package com.restaurant.configuration.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ConfigEntryRepository extends JpaRepository<ConfigEntryEntity,UUID>{
 Optional<ConfigEntryEntity> findByTenantIdAndScopeAndScopeIdIsNullAndKey(UUID tenant,String scope,String key);
 Optional<ConfigEntryEntity> findByTenantIdAndScopeAndScopeIdAndKey(UUID tenant,String scope,UUID scopeId,String key);
}
