package com.restaurant.inventory.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.Optional; import java.util.UUID;
public interface RecipeVersionRepository extends JpaRepository<RecipeVersionEntity, UUID> {
  Optional<RecipeVersionEntity> findFirstByVariantIdOrderByVersionNoDesc(UUID variantId);
  List<RecipeVersionEntity> findByVariantId(UUID variantId);
}
