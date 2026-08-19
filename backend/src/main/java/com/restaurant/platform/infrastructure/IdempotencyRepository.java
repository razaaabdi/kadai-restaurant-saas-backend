package com.restaurant.platform.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, IdempotencyId> {}
