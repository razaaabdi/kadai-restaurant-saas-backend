package com.restaurant.outlet.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface OutletRepository extends JpaRepository<OutletEntity, UUID> {}
