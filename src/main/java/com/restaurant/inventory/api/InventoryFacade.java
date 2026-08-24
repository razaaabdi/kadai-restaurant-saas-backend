package com.restaurant.inventory.api;

import com.restaurant.inventory.infrastructure.InventoryItemEntity;
import com.restaurant.inventory.infrastructure.InventoryItemRepository;
import com.restaurant.inventory.infrastructure.RecipeLineEntity;
import com.restaurant.inventory.infrastructure.RecipeLineRepository;
import com.restaurant.inventory.infrastructure.RecipeVersionEntity;
import com.restaurant.inventory.infrastructure.RecipeVersionRepository;
import com.restaurant.inventory.infrastructure.StockBalanceEntity;
import com.restaurant.inventory.infrastructure.StockBalanceRepository;
import com.restaurant.inventory.infrastructure.StockTransactionEntity;
import com.restaurant.inventory.infrastructure.StockTransactionRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.Quantity;
import com.restaurant.platform.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryFacade {
	private final InventoryItemRepository items;
	private final RecipeVersionRepository recipes;
	private final RecipeLineRepository lines;
	private final StockTransactionRepository txs;
	private final StockBalanceRepository balances;

	public InventoryFacade(InventoryItemRepository items, RecipeVersionRepository recipes, RecipeLineRepository lines,
			StockTransactionRepository txs, StockBalanceRepository balances) {
		this.items = items;
		this.recipes = recipes;
		this.lines = lines;
		this.txs = txs;
		this.balances = balances;
	}

	@Transactional
	public Map<String, Object> createItem(UUID outletId, String name, String unit, String qtyRaw) {
		if (name == null || name.isBlank()) {
			throw ApiException.bad("ITEM_NAME", "Name is required");
		}
		String uom = (unit == null || unit.isBlank()) ? "g" : unit.trim();
		BigDecimal opening = openingQty(qtyRaw);
		InventoryItemEntity e = new InventoryItemEntity();
		e.setTenantId(TenantContext.require().tenantId());
		e.setOutletId(outletId);
		e.setName(name.trim());
		e.setUnit(uom);
		items.save(e);
		if (opening.compareTo(BigDecimal.ZERO) > 0) {
			apply(outletId, e.getId(), "PURCHASE", opening, null, true);
		}
		return Map.of("id", e.getId(), "name", name.trim(), "unit", uom, "qty", opening);
	}

	@Transactional
	public Map<String, Object> createRecipe(UUID variantId, UUID inventoryItemId, String qty) {
		RecipeVersionEntity v = new RecipeVersionEntity();
		v.setTenantId(TenantContext.require().tenantId());
		v.setVariantId(variantId);
		v.setVersionNo(recipes.findByVariantId(variantId).size() + 1);
		recipes.save(v);
		RecipeLineEntity l = new RecipeLineEntity();
		l.setTenantId(TenantContext.require().tenantId());
		l.setRecipeVersionId(v.getId());
		l.setInventoryItemId(inventoryItemId);
		l.setQty(new Quantity(new BigDecimal(qty)).value());
		lines.save(l);
		return Map.of("recipeVersionId", v.getId());
	}

	@Transactional
	public Map<String, Object> purchase(UUID outletId, UUID inventoryItemId, String qty) {
		apply(outletId, inventoryItemId, "PURCHASE", new Quantity(new BigDecimal(qty)).value(), null, true);
		return Map.of("ok", true);
	}

	public UUID latestRecipe(UUID variantId) {
		return recipes.findFirstByVariantIdOrderByVersionNoDesc(variantId).map(RecipeVersionEntity::getId).orElse(null);
	}

	@Transactional
	public void deductSale(UUID outletId, UUID orderId, UUID recipeVersionId, BigDecimal portions, boolean allowNegative) {
		if (recipeVersionId == null) return;
		for (RecipeLineEntity line : lines.findByRecipeVersionId(recipeVersionId)) {
			if (line.getModifierId() != null) continue;
			BigDecimal q = line.getQty().multiply(portions).negate();
			apply(outletId, line.getInventoryItemId(), "SALE", q, orderId, allowNegative);
		}
	}

	@Transactional
	public void reverseVoid(UUID outletId, UUID orderId) {
		for (StockTransactionEntity tx : txs.findByOrderId(orderId)) {
			if (!"SALE".equals(tx.getType())) continue;
			apply(outletId, tx.getInventoryItemId(), "VOID_REVERSAL", tx.getQty().negate(), orderId, true);
		}
	}

	public List<StockTransactionEntity> ledger(UUID outletId, UUID inventoryItemId) {
		return txs.findByOutletIdAndInventoryItemId(outletId, inventoryItemId);
	}

	public BigDecimal balance(UUID outletId, UUID inventoryItemId) {
		return balances.findByOutletIdAndInventoryItemId(outletId, inventoryItemId)
				.map(StockBalanceEntity::getQty).orElse(BigDecimal.ZERO);
	}

	private static BigDecimal openingQty(String qtyRaw) {
		if (qtyRaw == null || qtyRaw.isBlank() || "null".equalsIgnoreCase(qtyRaw)) {
			return BigDecimal.ZERO.setScale(4);
		}
		try {
			BigDecimal qty = new Quantity(new BigDecimal(qtyRaw.trim())).value();
			if (qty.compareTo(BigDecimal.ZERO) < 0) {
				throw ApiException.bad("ITEM_QTY", "Qty cannot be negative");
			}
			return qty;
		} catch (NumberFormatException ex) {
			throw ApiException.bad("ITEM_QTY", "Qty must be a number");
		}
	}

	private void apply(UUID outletId, UUID inventoryItemId, String type, BigDecimal qty, UUID orderId, boolean allowNegative) {
		StockTransactionEntity tx = new StockTransactionEntity();
		tx.setTenantId(TenantContext.require().tenantId());
		tx.setOutletId(outletId);
		tx.setInventoryItemId(inventoryItemId);
		tx.setType(type);
		tx.setQty(qty);
		tx.setOrderId(orderId);
		txs.save(tx);
		StockBalanceEntity b = balances.findByOutletIdAndInventoryItemId(outletId, inventoryItemId).orElseGet(() -> {
			StockBalanceEntity n = new StockBalanceEntity();
			n.setTenantId(TenantContext.require().tenantId());
			n.setOutletId(outletId);
			n.setInventoryItemId(inventoryItemId);
			n.setQty(java.math.BigDecimal.ZERO);
			return balances.save(n);
		});
		BigDecimal next = b.getQty().add(qty);
		if (!allowNegative && next.compareTo(BigDecimal.ZERO) < 0) {
			throw ApiException.conflict("STOCK", "Insufficient stock");
		}
		b.setQty(next);
		balances.save(b);
	}
}
