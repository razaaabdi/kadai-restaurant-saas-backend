package com.restaurant.inventory.infrastructure;
import jakarta.persistence.*;
import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="recipe_lines")
public class RecipeLineEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private UUID recipeVersionId; private UUID inventoryItemId;
  private BigDecimal qty; private UUID modifierId;
  public UUID getRecipeVersionId(){return recipeVersionId;}
  public UUID getInventoryItemId(){return inventoryItemId;}
  public BigDecimal getQty(){return qty;} public UUID getModifierId(){return modifierId;}
  public void setTenantId(UUID t){tenantId=t;} public void setRecipeVersionId(UUID r){recipeVersionId=r;}
  public void setInventoryItemId(UUID i){inventoryItemId=i;} public void setQty(BigDecimal q){qty=q;}
  public void setModifierId(UUID m){modifierId=m;}
}
