package com.restaurant.inventory.infrastructure;
import jakarta.persistence.*;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="stock_transactions")
public class StockTransactionEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private UUID outletId; private UUID inventoryItemId;
  private String type; private BigDecimal qty; private UUID orderId; private Instant createdAt = Instant.now();
  public UUID getId(){return id;} public UUID getInventoryItemId(){return inventoryItemId;}
  public String getType(){return type;} public BigDecimal getQty(){return qty;}
  public void setTenantId(UUID t){tenantId=t;} public void setOutletId(UUID o){outletId=o;}
  public void setInventoryItemId(UUID i){inventoryItemId=i;} public void setType(String t){type=t;}
  public void setQty(BigDecimal q){qty=q;} public void setOrderId(UUID o){orderId=o;}
}
