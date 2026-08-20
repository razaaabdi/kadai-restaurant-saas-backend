package com.restaurant.inventory.infrastructure;
import jakarta.persistence.*;
import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="stock_balances")
public class StockBalanceEntity {
  @Id private UUID id = UUID.randomUUID();
  private UUID tenantId; private UUID outletId; private UUID inventoryItemId;
	private BigDecimal qty = BigDecimal.ZERO;
  public UUID getInventoryItemId(){return inventoryItemId;} public BigDecimal getQty(){return qty;}
  public void setQty(BigDecimal q){qty=q;}
  public void setTenantId(UUID t){tenantId=t;} public void setOutletId(UUID o){outletId=o;}
  public void setInventoryItemId(UUID i){inventoryItemId=i;}
}
