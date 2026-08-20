package com.restaurant.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserOutletRepository extends JpaRepository<UserOutletEntity, UserOutletId> {
	List<UserOutletEntity> findByUserId(UUID userId);
}
