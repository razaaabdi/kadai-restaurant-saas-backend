package com.restaurant.reporting.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySalesRepository extends JpaRepository<DailySalesEntity, DailySalesId> {}
