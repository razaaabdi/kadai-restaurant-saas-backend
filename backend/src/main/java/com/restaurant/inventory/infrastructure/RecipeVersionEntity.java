package com.restaurant.inventory.infrastructure;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="recipe_versions")
public class RecipeVersionEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private UUID variantId; private int versionNo;
  public UUID getId(){return id;} public UUID getVariantId(){return variantId;}
  public void setTenantId(UUID t){tenantId=t;} public void setVariantId(UUID v){variantId=v;}
  public void setVersionNo(int n){versionNo=n;}
}
