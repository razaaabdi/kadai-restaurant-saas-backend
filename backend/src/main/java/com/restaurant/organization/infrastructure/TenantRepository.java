package com.restaurant.organization.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
	Optional<TenantEntity> findBySlug(String slug);
	boolean existsBySlug(String slug);
}
