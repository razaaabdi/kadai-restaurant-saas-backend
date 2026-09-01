package com.restaurant.inventory.api;

import com.restaurant.inventory.application.StockLedgerService;
import com.restaurant.inventory.domain.StockStatus;
import com.restaurant.inventory.domain.StockTransactionType;
import com.restaurant.inventory.domain.WastageReason;
import com.restaurant.inventory.infrastructure.InventoryCategoryEntity;
import com.restaurant.inventory.infrastructure.InventoryCategoryRepository;
import com.restaurant.inventory.infrastructure.InventoryItemEntity;
import com.restaurant.inventory.infrastructure.InventoryItemRepository;
import com.restaurant.inventory.infrastructure.RecipeLineEntity;
import com.restaurant.inventory.infrastructure.RecipeLineRepository;
import com.restaurant.inventory.infrastructure.RecipeVersionEntity;
import com.restaurant.inventory.infrastructure.RecipeVersionRepository;
import com.restaurant.inventory.infrastructure.StockBalanceEntity;
import com.restaurant.inventory.infrastructure.StockBalanceRepository;
import com.restaurant.inventory.infrastructure.StockLocationEntity;
import com.restaurant.inventory.infrastructure.StockLocationRepository;
import com.restaurant.inventory.infrastructure.StockTransactionEntity;
import com.restaurant.inventory.infrastructure.StockTransactionRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.AuditWriter;
import com.restaurant.platform.api.OutboxPublisher;
import com.restaurant.platform.api.Quantity;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryFacade {
	private final InventoryItemRepository items;
	private final RecipeVersionRepository recipes;
	private final RecipeLineRepository lines;
	private final StockTransactionRepository txs;
	private final StockBalanceRepository balances;
	private final InventoryCategoryRepository categories;
	private final StockLocationRepository locations;
	private final StockLedgerService ledger;
	private final AuditWriter audit;
	private final OutboxPublisher outbox;
	private final JdbcTemplate jdbc;

	public InventoryFacade(InventoryItemRepository items, RecipeVersionRepository recipes, RecipeLineRepository lines,
			StockTransactionRepository txs, StockBalanceRepository balances, InventoryCategoryRepository categories,
			StockLocationRepository locations, StockLedgerService ledger, AuditWriter audit, OutboxPublisher outbox, JdbcTemplate jdbc) {
		this.items = items;
		this.recipes = recipes;
		this.lines = lines;
		this.txs = txs;
		this.balances = balances;
		this.categories = categories;
		this.locations = locations;
		this.ledger = ledger;
		this.audit = audit;
		this.outbox = outbox;
		this.jdbc = jdbc;
	}

	@Transactional
	public Map<String, Object> createItem(UUID outletId, String name, String unit, String qtyRaw) {
		return createItem(outletId, name, unit, qtyRaw, null, null, null, null, "0", "0");
	}

	@Transactional
	public Map<String, Object> createItem(UUID outletId, String name, String unit, String qtyRaw, String sku,
			UUID categoryId, UUID locationId, String openingUnitCostPaise, String minimumStock, String reorderLevel) {
		requireStaff();
		requireOutletAccess(outletId);
		if (name == null || name.isBlank()) throw ApiException.bad("ITEM_NAME", "Name is required");
		String uom = (unit == null || unit.isBlank() || "null".equalsIgnoreCase(unit)) ? "g" : unit.trim();
		BigDecimal opening = openingQty(qtyRaw);
		if (categoryId != null) {
			categories.findByIdAndTenantId(categoryId, TenantContext.require().tenantId())
					.orElseThrow(() -> ApiException.notFound("CATEGORY", "Inventory category not found"));
		}
		InventoryItemEntity e = new InventoryItemEntity();
		e.setTenantId(TenantContext.require().tenantId());
		e.setOutletId(outletId);
		e.setName(name.trim());
		e.setUnit(uom);
		e.setSku(normalizeSku(sku));
		e.setCategoryId(categoryId);
		e.setMinimumStock(qtyOrZero(minimumStock));
		e.setReorderLevel(qtyOrZero(reorderLevel));
		if (e.getSku() != null && items.existsByTenantIdAndSkuIgnoreCase(e.getTenantId(), e.getSku())) {
			throw ApiException.conflict("SKU_EXISTS", "An item with this SKU already exists");
		}
		items.save(e);
		if (opening.compareTo(BigDecimal.ZERO) > 0) {
			ledger.post(outletId, e.getId(), locationId, StockTransactionType.OPENING_STOCK, opening, money(openingUnitCostPaise),
					e.getId(), "OPENING_STOCK", null, null, true);
		}
		audit.write("INVENTORY_ITEM_CREATE", "INVENTORY_ITEM", e.getId(), e.getName());
		return itemView(e);
	}

	@Transactional
	public Map<String, Object> updateItem(UUID itemId, String name, String unit) {
		return updateItem(itemId, name, unit, null, null, null, null, null);
	}

	@Transactional
	public Map<String, Object> updateItem(UUID itemId, String name, String unit, String sku, UUID categoryId,
			String minimumStock, String reorderLevel, Long expectedVersion) {
		InventoryItemEntity e = requireItem(itemId);
		requireOutletAccess(e.getOutletId());
		if (expectedVersion != null && expectedVersion != e.getVersion()) {
			throw ApiException.conflict("VERSION_CONFLICT", "Inventory item changed; reload before saving");
		}
		if (name == null || name.isBlank()) throw ApiException.bad("ITEM_NAME", "Name is required");
		String uom = (unit == null || unit.isBlank() || "null".equalsIgnoreCase(unit)) ? e.getUnit() : unit.trim();
		if (uom == null || uom.isBlank()) uom = "g";
		if (categoryId != null) {
			categories.findByIdAndTenantId(categoryId, TenantContext.require().tenantId())
					.orElseThrow(() -> ApiException.notFound("CATEGORY", "Inventory category not found"));
			e.setCategoryId(categoryId);
		}
		if (sku != null) {
			String nextSku = normalizeSku(sku);
			if (nextSku != null && !nextSku.equalsIgnoreCase(e.getSku())
					&& items.existsByTenantIdAndSkuIgnoreCase(e.getTenantId(), nextSku)) {
				throw ApiException.conflict("SKU_EXISTS", "An item with this SKU already exists");
			}
			e.setSku(nextSku);
		}
		e.setName(name.trim());
		e.setUnit(uom);
		if (minimumStock != null) e.setMinimumStock(qtyOrZero(minimumStock));
		if (reorderLevel != null) e.setReorderLevel(qtyOrZero(reorderLevel));
		e.setUpdatedAt(Instant.now());
		items.save(e);
		audit.write("INVENTORY_ITEM_EDIT", "INVENTORY_ITEM", e.getId(), e.getName());
		return itemView(e);
	}

	@Transactional
	public Map<String, Object> deactivate(UUID itemId) {
		InventoryItemEntity e = requireItem(itemId);
		requireOutletAccess(e.getOutletId());
		e.setActive(false);
		e.setUpdatedAt(Instant.now());
		items.save(e);
		audit.write("INVENTORY_ITEM_DEACTIVATE", "INVENTORY_ITEM", e.getId(), e.getName());
		return itemView(e);
	}

	public Map<String, Object> getItem(UUID itemId) {
		InventoryItemEntity e = requireItem(itemId);
		requireOutletAccess(e.getOutletId());
		return itemView(e);
	}

	public Map<String, Object> listItems(UUID outletId, String q, String status, int page, int size) {
		requireStaff();
		requireOutletAccess(outletId);
		Pageable pageable = PageRequest.of(Math.max(page, 0), bound(size), Sort.by("name"));
		Page<InventoryItemEntity> result = (q == null || q.isBlank())
				? items.findByOutletId(outletId, pageable)
				: items.findByOutletIdAndNameContainingIgnoreCase(outletId, q.trim(), pageable);
		List<Map<String, Object>> content = new ArrayList<>();
		for (InventoryItemEntity item : result.getContent()) {
			Map<String, Object> view = itemView(item);
			if (status == null || status.isBlank() || status.equalsIgnoreCase(String.valueOf(view.get("status")))) {
				content.add(view);
			}
		}
		return Map.of("content", content, "page", result.getNumber(), "size", result.getSize(),
				"totalElements", result.getTotalElements(), "totalPages", result.getTotalPages());
	}

	public Map<String, Object> stock(UUID outletId, UUID locationId) {
		requireStaff();
		requireOutletAccess(outletId);
		UUID loc = locationId != null ? locationId : ledger.defaultLocation(outletId).getId();
		List<Map<String, Object>> rows = new ArrayList<>();
		for (InventoryItemEntity item : items.findByOutletIdOrderByNameAsc(outletId)) {
			if (!item.isActive()) continue;
			BigDecimal qty = qtyOf(outletId, loc, item.getId());
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("inventoryItemId", item.getId());
			row.put("name", item.getName());
			row.put("sku", item.getSku());
			row.put("unit", item.getUnit());
			row.put("qty", qty.toPlainString());
			row.put("stockLocationId", loc);
			row.put("status", StockStatus.of(qty, item.getReorderLevel()).name());
			rows.add(row);
		}
		return Map.of("outletId", outletId, "stockLocationId", loc, "content", rows);
	}

	public Map<String, Object> movements(UUID itemId, int page, int size) {
		InventoryItemEntity item = requireItem(itemId);
		requireOutletAccess(item.getOutletId());
		List<StockTransactionEntity> all = txs.findByOutletIdAndInventoryItemIdOrderByCreatedAtAsc(item.getOutletId(), itemId);
		BigDecimal running = BigDecimal.ZERO.setScale(4);
		List<Map<String, Object>> enriched = new ArrayList<>();
		for (StockTransactionEntity tx : all) {
			BigDecimal before = running;
			running = running.add(tx.getQty());
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", tx.getId());
			row.put("createdAt", tx.getCreatedAt().toString());
			row.put("type", tx.getType());
			row.put("quantity", tx.getQty().toPlainString());
			row.put("before", before.toPlainString());
			row.put("after", running.toPlainString());
			row.put("unitCostPaise", tx.getUnitCostPaise());
			row.put("totalCostPaise", tx.getTotalCostPaise());
			row.put("referenceType", tx.getReferenceType() == null ? "" : tx.getReferenceType());
			row.put("referenceId", tx.getReferenceId() == null ? "" : tx.getReferenceId().toString());
			row.put("performedBy", tx.getPerformedBy() == null ? "" : tx.getPerformedBy().toString());
			row.put("reason", tx.getReason() == null ? "" : tx.getReason());
			enriched.add(row);
		}
		java.util.Collections.reverse(enriched);
		int p = Math.max(page, 0);
		int s = bound(size);
		int total = enriched.size();
		int from = Math.min(p * s, total);
		int to = Math.min(from + s, total);
		List<Map<String, Object>> slice = new ArrayList<>(enriched.subList(from, to));
		return Map.of("content", slice, "page", p, "size", s, "totalElements", total,
				"totalPages", (total + s - 1) / s);
	}

	@Transactional
	public Map<String, Object> adjust(UUID outletId, UUID inventoryItemId, UUID locationId, String type, String qtyRaw,
			String reason, String notes, String unitCostPaise) {
		requireStaff();
		requireOutletAccess(outletId);
		requireItem(inventoryItemId);
		StockTransactionType txType;
		try {
			txType = StockTransactionType.valueOf(type == null ? "" : type.trim().toUpperCase(Locale.ROOT));
		} catch (Exception ex) {
			throw ApiException.bad("ADJUSTMENT_TYPE", "Adjustment type must be ADJUSTMENT_IN or ADJUSTMENT_OUT");
		}
		if (txType != StockTransactionType.ADJUSTMENT_IN && txType != StockTransactionType.ADJUSTMENT_OUT) {
			throw ApiException.bad("ADJUSTMENT_TYPE", "Adjustment type must be ADJUSTMENT_IN or ADJUSTMENT_OUT");
		}
		if (reason == null || reason.isBlank()) throw ApiException.bad("REASON", "Reason is required");
		BigDecimal qty = positiveQty(qtyRaw);
		if (txType == StockTransactionType.ADJUSTMENT_OUT) qty = qty.negate();
		var tx = ledger.post(outletId, inventoryItemId, locationId, txType, qty, money(unitCostPaise), null, "ADJUSTMENT",
				reason.trim(), notes, true);
		audit.write("STOCK_ADJUST", "STOCK_TRANSACTION", tx.getId(), txType.name());
		outbox.publish(TenantContext.require().tenantId(), "InventoryAdjusted", tx.getId().toString());
		Map<String, Object> res = new LinkedHashMap<>();
		res.put("id", tx.getId());
		res.put("type", tx.getType());
		res.put("qty", tx.getQty().toPlainString());
		res.put("balance", balance(outletId, inventoryItemId).toPlainString());
		return res;
	}

	@Transactional
	public Map<String, Object> wastage(UUID outletId, UUID inventoryItemId, UUID locationId, String qtyRaw, String reason,
			String notes) {
		requireStaff();
		requireOutletAccess(outletId);
		requireItem(inventoryItemId);
		WastageReason parsed;
		try {
			parsed = WastageReason.valueOf(reason == null ? "" : reason.trim().toUpperCase(Locale.ROOT));
		} catch (Exception ex) {
			throw ApiException.bad("WASTAGE_REASON", "Unknown wastage reason");
		}
		BigDecimal qty = positiveQty(qtyRaw).negate();
		var tx = ledger.post(outletId, inventoryItemId, locationId, StockTransactionType.WASTAGE, qty, 0L, null, "WASTAGE",
				parsed.name(), notes, true);
		audit.write("WASTAGE_CREATE", "STOCK_TRANSACTION", tx.getId(), parsed.name());
		outbox.publish(TenantContext.require().tenantId(), "WastageRecorded", tx.getId().toString());
		return Map.of("id", tx.getId(), "type", tx.getType(), "qty", tx.getQty().toPlainString(),
				"reason", parsed.name(), "balance", balance(outletId, inventoryItemId).toPlainString());
	}

	public Map<String, Object> dashboard(UUID outletId) {
		requireStaff();
		requireOutletAccess(outletId);
		long value = 0;
		int low = 0;
		int out = 0;
		for (InventoryItemEntity item : items.findByOutletIdOrderByNameAsc(outletId)) {
			if (!item.isActive()) continue;
			BigDecimal qty = balance(outletId, item.getId());
			StockStatus st = StockStatus.of(qty, item.getReorderLevel());
			if (st == StockStatus.LOW_STOCK) low++;
			if (st == StockStatus.OUT_OF_STOCK) out++;
			for (StockBalanceEntity b : balances.findAllByOutletIdAndInventoryItemId(outletId, item.getId())) {
				value += b.getInventoryValuePaise();
			}
		}
		Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
		long wastage = txs.findByOutletIdAndTypeAndCreatedAtGreaterThanEqual(outletId, "WASTAGE", start).stream()
				.mapToLong(StockTransactionEntity::getTotalCostPaise).sum();
		List<Map<String, Object>> recent = txs.findByOutletIdOrderByCreatedAtDesc(outletId, PageRequest.of(0, 10))
				.getContent().stream().map(tx -> Map.<String, Object>of(
						"id", tx.getId(), "type", tx.getType(), "qty", tx.getQty().toPlainString(),
						"createdAt", tx.getCreatedAt().toString(), "inventoryItemId", tx.getInventoryItemId()))
				.toList();
		Map<String, Object> dash = new LinkedHashMap<>();
		dash.put("outletId", outletId);
		dash.put("totalInventoryValuePaise", value);
		dash.put("lowStockItems", low);
		dash.put("outOfStockItems", out);
		dash.put("todayWastageCostPaise", wastage);
		dash.put("recentMovements", recent);
		return dash;
	}

	@Transactional
	public Map<String, Object> createCategory(String name, String description) {
		requireStaff();
		if (name == null || name.isBlank()) throw ApiException.bad("CATEGORY_NAME", "Name is required");
		UUID tenantId = TenantContext.require().tenantId();
		if (categories.existsByTenantIdAndNameIgnoreCase(tenantId, name.trim())) {
			throw ApiException.conflict("CATEGORY_EXISTS", "A category with this name already exists");
		}
		InventoryCategoryEntity e = new InventoryCategoryEntity();
		e.setTenantId(tenantId);
		e.setName(name.trim());
		e.setDescription(description == null ? "" : description.trim());
		categories.save(e);
		return Map.of("id", e.getId(), "name", e.getName(), "description", e.getDescription(), "active", e.isActive());
	}

	public List<Map<String, Object>> listCategories() {
		requireStaff();
		return categories.findByTenantIdOrderByNameAsc(TenantContext.require().tenantId()).stream()
				.map(c -> Map.<String, Object>of("id", c.getId(), "name", c.getName(), "description", c.getDescription(),
						"active", c.isActive()))
				.toList();
	}

	@Transactional
	public Map<String, Object> createLocation(UUID outletId, String name, String type) {
		requireStaff();
		requireOutletAccess(outletId);
		if (name == null || name.isBlank()) throw ApiException.bad("LOCATION_NAME", "Name is required");
		if (locations.existsByOutletIdAndNameIgnoreCase(outletId, name.trim())) {
			throw ApiException.conflict("LOCATION_EXISTS", "A stock location with this name already exists");
		}
		StockLocationEntity e = new StockLocationEntity();
		e.setTenantId(TenantContext.require().tenantId());
		e.setOutletId(outletId);
		e.setName(name.trim());
		e.setType(type == null || type.isBlank() ? "STORE" : type.trim().toUpperCase(Locale.ROOT));
		locations.save(e);
		return Map.of("id", e.getId(), "outletId", outletId, "name", e.getName(), "type", e.getType(), "active", e.isActive());
	}

	public List<Map<String, Object>> listLocations(UUID outletId) {
		requireStaff();
		requireOutletAccess(outletId);
		ledger.defaultLocation(outletId);
		return locations.findByOutletIdOrderByNameAsc(outletId).stream()
				.map(l -> Map.<String, Object>of("id", l.getId(), "outletId", l.getOutletId(), "name", l.getName(),
						"type", l.getType(), "active", l.isActive()))
				.toList();
	}

	@Transactional
	public Map<String, Object> createRecipe(UUID variantId, UUID inventoryItemId, String qty) {
		return createRecipe(variantId,List.of(Map.of("inventoryItemId",inventoryItemId,"qty",qty)));
	}

	@Transactional
	public Map<String,Object> createRecipe(UUID variantId,List<Map<String,Object>> ingredients) {
		requireStaff();
		if(variantId==null)throw ApiException.bad("VARIANT","Variant is required");
		if(ingredients==null||ingredients.isEmpty())throw ApiException.bad("RECIPE_LINES","Add at least one ingredient");
		if(ingredients.size()>100)throw ApiException.bad("RECIPE_LINES","A recipe can contain at most 100 ingredients");
		RecipeVersionEntity v = new RecipeVersionEntity();
		v.setTenantId(TenantContext.require().tenantId());
		v.setVariantId(variantId);
		int versionNo=recipes.findByVariantId(variantId).size()+1;
		v.setVersionNo(versionNo);
		recipes.save(v);
		java.util.Set<UUID> seen=new java.util.HashSet<>();
		for(Map<String,Object> ingredient:ingredients){UUID inventoryItemId;try{inventoryItemId=UUID.fromString(String.valueOf(ingredient.get("inventoryItemId")));}catch(Exception e){throw ApiException.bad("RECIPE_ITEM","Each ingredient requires a valid inventoryItemId");}if(!seen.add(inventoryItemId))throw ApiException.bad("RECIPE_DUPLICATE","An ingredient can appear only once per recipe version");InventoryItemEntity item=requireItem(inventoryItemId);requireOutletAccess(item.getOutletId());BigDecimal quantity;try{quantity=new Quantity(new BigDecimal(String.valueOf(ingredient.get("qty")))).value();}catch(Exception e){throw ApiException.bad("RECIPE_QUANTITY","Each ingredient requires a valid quantity");}if(quantity.compareTo(BigDecimal.ZERO)<=0)throw ApiException.bad("RECIPE_QUANTITY","Ingredient quantity must be greater than zero");RecipeLineEntity l=new RecipeLineEntity();l.setTenantId(TenantContext.require().tenantId());l.setRecipeVersionId(v.getId());l.setInventoryItemId(inventoryItemId);l.setQty(quantity);lines.save(l);}
		audit.write("RECIPE_VERSION_CREATED","RECIPE_VERSION",v.getId(),"variant="+variantId+" ingredients="+ingredients.size());
		return Map.of("recipeVersionId",v.getId(),"versionNo",versionNo,"ingredientCount",ingredients.size());
	}

	@Transactional
	public Map<String, Object> purchase(UUID outletId, UUID inventoryItemId, String qty) {
		requireOutletAccess(outletId);
		ledger.post(outletId, inventoryItemId, null, StockTransactionType.PURCHASE, new Quantity(new BigDecimal(qty)).value(),
				0L, null, "PURCHASE", null, null, true);
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
			ledger.post(outletId, line.getInventoryItemId(), null, StockTransactionType.SALE_CONSUMPTION, q, 0L, orderId,
					"ORDER", null, null, allowNegative);
		}
	}

	@Transactional
	public boolean consumeCompletedOrder(UUID outletId,UUID orderId,List<RecipeConsumption> consumptions,boolean allowNegative){var p=TenantContext.require();int inserted=jdbc.update("insert into inventory_order_consumptions(order_id,tenant_id,outlet_id) values(?,?,?) on conflict(order_id) do nothing",orderId,p.tenantId(),outletId);if(inserted==0)return false;for(RecipeConsumption consumption:consumptions)deductSale(outletId,orderId,consumption.recipeVersionId(),consumption.portions(),allowNegative);audit.write("ORDER_INVENTORY_CONSUMED","ORDER",orderId,"recipeLines="+consumptions.size());return true;}

	@Transactional
	public void reverseVoid(UUID outletId, UUID orderId) {
		for (StockTransactionEntity tx : txs.findByOrderId(orderId)) {
			if (!"SALE".equals(tx.getType()) && !"SALE_CONSUMPTION".equals(tx.getType())) continue;
			ledger.post(outletId, tx.getInventoryItemId(), tx.getStockLocationId(), StockTransactionType.VOID_REVERSAL,
					tx.getQty().negate(), tx.getUnitCostPaise(), orderId, "ORDER", null, null, true);
		}
	}

	public List<StockTransactionEntity> ledger(UUID outletId, UUID inventoryItemId) {
		return txs.findByOutletIdAndInventoryItemId(outletId, inventoryItemId);
	}

	public BigDecimal balance(UUID outletId, UUID inventoryItemId) {
		return balances.findAllByOutletIdAndInventoryItemId(outletId, inventoryItemId).stream()
				.map(StockBalanceEntity::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private Map<String, Object> itemView(InventoryItemEntity e) {
		BigDecimal qty = balance(e.getOutletId(), e.getId());
		long avg = 0;
		long value = 0;
		for (StockBalanceEntity b : balances.findAllByOutletIdAndInventoryItemId(e.getOutletId(), e.getId())) {
			avg = b.getAverageCostPaise();
			value += b.getInventoryValuePaise();
		}
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("id", e.getId());
		view.put("outletId", e.getOutletId());
		view.put("name", e.getName());
		view.put("unit", e.getUnit());
		view.put("sku", e.getSku() == null ? "" : e.getSku());
		view.put("categoryId", e.getCategoryId() == null ? "" : e.getCategoryId().toString());
		view.put("qty", qty.toPlainString());
		view.put("currentStock", qty.toPlainString());
		view.put("averageCostPaise", avg);
		view.put("inventoryValuePaise", value);
		view.put("minimumStock", e.getMinimumStock().toPlainString());
		view.put("reorderLevel", e.getReorderLevel().toPlainString());
		view.put("status", StockStatus.of(qty, e.getReorderLevel()).name());
		view.put("active", e.isActive());
		view.put("version", e.getVersion());
		return view;
	}

	private InventoryItemEntity requireItem(UUID itemId) {
		InventoryItemEntity e = items.findById(itemId).orElseThrow(() -> ApiException.notFound("ITEM", "Inventory item not found"));
		var p = TenantContext.require();
		if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Guests cannot manage inventory");
		if (!p.tenantId().equals(e.getTenantId())) throw ApiException.notFound("ITEM", "Inventory item not found");
		return e;
	}

	private void requireStaff() {
		if (TenantContext.require().isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Guests cannot manage inventory");
	}

	private void requireOutletAccess(UUID outletId) {
		TenantPrincipal p = TenantContext.require();
		if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Guests cannot manage inventory");
		if (p.outletIds() == null || !p.outletIds().contains(outletId)) {
			throw ApiException.forbidden("OUTLET_ACCESS", "You do not have access to this outlet");
		}
	}

	private BigDecimal qtyOf(UUID outletId, UUID locationId, UUID itemId) {
		return balances.findAllByOutletIdAndInventoryItemId(outletId, itemId).stream()
				.filter(b -> locationId.equals(b.getStockLocationId()))
				.map(StockBalanceEntity::getQty).findFirst().orElse(BigDecimal.ZERO);
	}

	private static BigDecimal openingQty(String qtyRaw) {
		if (qtyRaw == null || qtyRaw.isBlank() || "null".equalsIgnoreCase(qtyRaw)) {
			return BigDecimal.ZERO.setScale(4);
		}
		try {
			BigDecimal qty = new Quantity(new BigDecimal(qtyRaw.trim())).value();
			if (qty.compareTo(BigDecimal.ZERO) < 0) throw ApiException.bad("ITEM_QTY", "Qty cannot be negative");
			return qty;
		} catch (NumberFormatException ex) {
			throw ApiException.bad("ITEM_QTY", "Qty must be a number");
		}
	}

	private static BigDecimal positiveQty(String qtyRaw) {
		BigDecimal qty = openingQty(qtyRaw);
		if (qty.compareTo(BigDecimal.ZERO) <= 0) throw ApiException.bad("ITEM_QTY", "Qty must be greater than zero");
		return qty;
	}

	private static BigDecimal qtyOrZero(String raw) {
		if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) return BigDecimal.ZERO.setScale(4);
		return openingQty(raw);
	}

	private static long money(String raw) {
		if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) return 0L;
		try {
			long v = Long.parseLong(raw.trim());
			if (v < 0) throw ApiException.bad("COST", "Cost cannot be negative");
			return v;
		} catch (NumberFormatException ex) {
			throw ApiException.bad("COST", "Cost must be a whole number of paise");
		}
	}

	private static String normalizeSku(String sku) {
		if (sku == null || sku.isBlank() || "null".equalsIgnoreCase(sku)) return null;
		return sku.trim().toUpperCase(Locale.ROOT);
	}

	private static int bound(int size) {
		if (size <= 0) return 25;
		return Math.min(size, 100);
	}
}
