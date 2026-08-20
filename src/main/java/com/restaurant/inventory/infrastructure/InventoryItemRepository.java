package com.restaurant.inventory.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, UUID> {}
