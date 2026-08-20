package com.restaurant.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
	@Query(value = "SELECT * FROM lookup_refresh_by_hash(:hash)", nativeQuery = true)
	List<RefreshTokenEntity> lookupByHash(@Param("hash") String hash);

	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}
