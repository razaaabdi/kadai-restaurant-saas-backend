package com.restaurant.organization.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface BrandRepository extends JpaRepository<BrandEntity, UUID> {}
