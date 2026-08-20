package com.restaurant.catalog.infrastructure;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="variants")
public class VariantEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private UUID itemId; private String name; private long pricePaise;
  public UUID getId(){return id;} public UUID getItemId(){return itemId;}
  public void setTenantId(UUID t){tenantId=t;} public void setItemId(UUID i){itemId=i;}
  public void setName(String n){name=n;} public String getName(){return name;}
  public void setPricePaise(long p){pricePaise=p;} public long getPricePaise(){return pricePaise;}
}
