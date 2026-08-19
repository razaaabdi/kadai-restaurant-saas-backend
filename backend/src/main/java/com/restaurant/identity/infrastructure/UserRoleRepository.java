package com.restaurant.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleId> {
	List<UserRoleEntity> findByUserId(UUID userId);
}
