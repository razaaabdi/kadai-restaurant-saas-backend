package com.restaurant.catalog.infrastructure;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="tax_codes")
public class TaxCodeEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private String code; private int rateBps;
  public UUID getId(){return id;}
  public void setTenantId(UUID t){tenantId=t;}
  public void setCode(String c){code=c;}
  public void setRateBps(int r){rateBps=r;}
  public int getRateBps(){return rateBps;}
}
