package com.restaurant.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
	@Query(value = "SELECT * FROM lookup_user_by_email(:email)", nativeQuery = true)
	List<UserEntity> lookupByEmail(@Param("email") String email);
	List<UserEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
}
