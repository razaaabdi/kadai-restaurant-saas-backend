package com.restaurant.inventory.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface RecipeLineRepository extends JpaRepository<RecipeLineEntity, UUID> {
  List<RecipeLineEntity> findByRecipeVersionId(UUID recipeVersionId);
}
