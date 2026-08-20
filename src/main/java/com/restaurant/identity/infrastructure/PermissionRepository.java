package com.restaurant.identity.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PermissionRepository extends JpaRepository<PermissionEntity,UUID>{List<PermissionEntity> findByTenantIdOrderByCategoryAscCodeAsc(UUID tenantId);}
