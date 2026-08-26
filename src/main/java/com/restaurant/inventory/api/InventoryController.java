package com.restaurant.inventory.api;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
	private final InventoryFacade inventory;

	public InventoryController(InventoryFacade inventory) {
		this.inventory = inventory;
	}

	@PostMapping("/outlets/{outletId}/inventory-items")
	public Map<String, Object> item(@PathVariable UUID outletId, @RequestBody Map<String, Object> body) {
		return inventory.createItem(outletId, str(body.get("name")), str(body.get("unit")), str(body.get("qty")));
	}

	@PutMapping("/inventory-items/{itemId}")
	public Map<String, Object> update(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
		return inventory.updateItem(itemId, str(body.get("name")), str(body.get("unit")));
	}

	@GetMapping("/inventory/items")
	public Map<String, Object> listItems(@RequestParam UUID outletId, @RequestParam(required = false) String q,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return inventory.listItems(outletId, q, status, page, size);
	}

	@PostMapping("/inventory/items")
	public Map<String, Object> createCatalogItem(@RequestBody Map<String, Object> body) {
		return inventory.createItem(uuid(body.get("outletId")), str(body.get("name")), str(body.get("unit")),
				str(first(body, "openingQty", "qty")), str(body.get("sku")), uuidOrNull(body.get("categoryId")),
				uuidOrNull(body.get("stockLocationId")), str(first(body, "unitCostPaise", "openingUnitCostPaise")),
				str(body.get("minimumStock")), str(body.get("reorderLevel")));
	}

	@GetMapping("/inventory/items/{id}")
	public Map<String, Object> getItem(@PathVariable UUID id) {
		return inventory.getItem(id);
	}

	@PutMapping("/inventory/items/{id}")
	public Map<String, Object> updateCatalogItem(@PathVariable UUID id,
			@RequestHeader(value = "If-Match", required = false) Long version, @RequestBody Map<String, Object> body) {
		return inventory.updateItem(id, str(body.get("name")), str(body.get("unit")), str(body.get("sku")),
				uuidOrNull(body.get("categoryId")), str(body.get("minimumStock")), str(body.get("reorderLevel")), version);
	}

	@PostMapping("/inventory/items/{id}/deactivate")
	public Map<String, Object> deactivate(@PathVariable UUID id) {
		return inventory.deactivate(id);
	}

	@GetMapping("/inventory/stock")
	public Map<String, Object> stock(@RequestParam UUID outletId, @RequestParam(required = false) UUID stockLocationId) {
		return inventory.stock(outletId, stockLocationId);
	}

	@GetMapping("/inventory/items/{id}/movements")
	public Map<String, Object> movements(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		return inventory.movements(id, page, size);
	}

	@PostMapping("/inventory/adjustments")
	public Map<String, Object> adjust(@RequestBody Map<String, Object> body) {
		return inventory.adjust(uuid(body.get("outletId")), uuid(body.get("inventoryItemId")),
				uuidOrNull(body.get("stockLocationId")), str(first(body, "type", "adjustmentType")),
				str(first(body, "quantity", "qty")), str(body.get("reason")), str(body.get("notes")),
				str(body.get("unitCostPaise")));
	}

	@PostMapping("/inventory/wastage")
	public Map<String, Object> wastage(@RequestBody Map<String, Object> body) {
		return inventory.wastage(uuid(body.get("outletId")), uuid(body.get("inventoryItemId")),
				uuidOrNull(body.get("stockLocationId")), str(first(body, "quantity", "qty")), str(body.get("reason")),
				str(body.get("notes")));
	}

	@GetMapping("/inventory/dashboard")
	public Map<String, Object> dashboard(@RequestParam UUID outletId) {
		return inventory.dashboard(outletId);
	}

	@GetMapping("/inventory/categories")
	public List<Map<String, Object>> categories() {
		return inventory.listCategories();
	}

	@PostMapping("/inventory/categories")
	public Map<String, Object> category(@RequestBody Map<String, Object> body) {
		return inventory.createCategory(str(body.get("name")), str(body.get("description")));
	}

	@GetMapping("/inventory/stock-locations")
	public List<Map<String, Object>> locations(@RequestParam UUID outletId) {
		return inventory.listLocations(outletId);
	}

	@PostMapping("/inventory/stock-locations")
	public Map<String, Object> location(@RequestBody Map<String, Object> body) {
		return inventory.createLocation(uuid(body.get("outletId")), str(body.get("name")), str(body.get("type")));
	}

	@PostMapping("/recipes")
	public Map<String, Object> recipe(@RequestBody Map<String, String> body) {
		return inventory.createRecipe(UUID.fromString(body.get("variantId")), UUID.fromString(body.get("inventoryItemId")),
				body.get("qty"));
	}

	@PostMapping("/stock/purchase")
	public Map<String, Object> purchase(@RequestHeader("Idempotency-Key") String key, @RequestBody Map<String, String> body) {
		return inventory.purchase(UUID.fromString(body.get("outletId")), UUID.fromString(body.get("inventoryItemId")),
				body.get("qty"));
	}

	@GetMapping("/stock/balance")
	public Map<String, Object> bal(@RequestParam UUID outletId, @RequestParam UUID inventoryItemId) {
		return Map.of("qty", inventory.balance(outletId, inventoryItemId));
	}

	private static String str(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static Object first(Map<String, Object> body, String a, String b) {
		if (body.get(a) != null) return body.get(a);
		return body.get(b);
	}

	private static UUID uuid(Object value) {
		if (value == null) throw com.restaurant.platform.api.ApiException.bad("VALIDATION", "Required id is missing");
		try {
			return UUID.fromString(String.valueOf(value));
		} catch (Exception ex) {
			throw com.restaurant.platform.api.ApiException.bad("VALIDATION", "Invalid id");
		}
	}

	private static UUID uuidOrNull(Object value) {
		if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) return null;
		return uuid(value);
	}
}
