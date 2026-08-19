package com.restaurant.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {
	List<RoleEntity> findByTenantId(UUID tenantId);
}
