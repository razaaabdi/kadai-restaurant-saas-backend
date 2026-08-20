package com.restaurant.identity.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity,RolePermissionId>{List<RolePermissionEntity> findByRoleIdIn(Collection<UUID> roleIds);}
