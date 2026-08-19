package com.restaurant.catalog.infrastructure;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="modifiers")
public class ModifierEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private UUID outletId; private String name; private long extraPaise;
  public UUID getId(){return id;}
  public void setTenantId(UUID t){tenantId=t;} public void setOutletId(UUID o){outletId=o;}
  public void setName(String n){name=n;} public String getName(){return name;}
  public void setExtraPaise(long p){extraPaise=p;} public long getExtraPaise(){return extraPaise;}
}
