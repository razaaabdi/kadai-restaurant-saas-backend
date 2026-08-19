package com.restaurant.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface OrderLineModifierRepository extends JpaRepository<OrderLineModifierEntity, UUID> {}
