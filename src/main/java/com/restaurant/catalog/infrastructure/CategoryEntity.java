package com.restaurant.catalog.infrastructure;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="categories")
public class CategoryEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private UUID outletId; private String name; private int sortOrder; private boolean deleted;
  public UUID getId(){return id;}
  public UUID getOutletId(){return outletId;} public UUID getTenantId(){return tenantId;} public boolean isDeleted(){return deleted;}
  public void setTenantId(UUID t){tenantId=t;} public void setOutletId(UUID o){outletId=o;}
  public void setName(String n){name=n;} public String getName(){return name;}
}
