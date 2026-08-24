package com.restaurant.inventory.infrastructure;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="inventory_items")
public class InventoryItemEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private UUID outletId; private String name; private String unit = "g";
  public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getOutletId(){return outletId;}
  public String getName(){return name;} public String getUnit(){return unit;}
  public void setTenantId(UUID t){tenantId=t;} public void setOutletId(UUID o){outletId=o;}
  public void setName(String n){name=n;} public void setUnit(String u){unit=u;}
}
