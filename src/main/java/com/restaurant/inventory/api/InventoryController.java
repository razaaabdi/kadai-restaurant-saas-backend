package com.restaurant.inventory.api;

import org.springframework.web.bind.annotation.*;

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
	public Map<String, Object> item(@PathVariable UUID outletId, @RequestBody Map<String, String> body) {
		return inventory.createItem(outletId, body.get("name"), body.getOrDefault("unit", "g"));
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
}
