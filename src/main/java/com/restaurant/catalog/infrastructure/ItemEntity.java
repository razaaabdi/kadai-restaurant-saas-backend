package com.restaurant.catalog.infrastructure;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="items")
public class ItemEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private UUID outletId; private UUID categoryId; private String name;
  private boolean availableOnQr = true; private boolean availableOnCounter = true;
  private boolean eightySixed; private UUID taxCodeId; private boolean deleted; private String description = ""; private String imageUrl;
  public UUID getId(){return id;}
  public void setTenantId(UUID t){tenantId=t;} public void setOutletId(UUID o){outletId=o;}
  public void setCategoryId(UUID c){categoryId=c;} public void setName(String n){name=n;}
  public String getName(){return name;} public void setTaxCodeId(UUID t){taxCodeId=t;}
  public UUID getTaxCodeId(){return taxCodeId;}
  public UUID getOutletId(){return outletId;} public UUID getCategoryId(){return categoryId;}
  public UUID getTenantId(){return tenantId;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
  public String getImageUrl(){return imageUrl;} public void setImageUrl(String v){imageUrl=v;}
  public boolean isAvailableOnQr(){return availableOnQr;} public void setAvailableOnQr(boolean v){availableOnQr=v;}
  public boolean isAvailableOnCounter(){return availableOnCounter;} public void setAvailableOnCounter(boolean v){availableOnCounter=v;}
  public boolean isEightySixed(){return eightySixed;} public void setEightySixed(boolean v){eightySixed=v;}
  public boolean isDeleted(){return deleted;}
  public void setDeleted(boolean v){deleted=v;}
}
