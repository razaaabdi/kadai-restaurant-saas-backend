package com.restaurant.catalog.api;

import com.restaurant.catalog.application.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
	private final CatalogService catalog;

	public CatalogController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@PostMapping("/tax-codes")
	public Map<String, Object> tax(@RequestBody Map<String, Object> body) {
		return catalog.createTax((String) body.get("code"), ((Number) body.get("rateBps")).intValue());
	}

	@PostMapping("/outlets/{outletId}/categories")
	public Map<String, Object> cat(@PathVariable UUID outletId, @RequestBody Map<String, String> body) {
		return catalog.createCategory(outletId, body.get("name"));
	}

	@GetMapping("/outlets/{outletId}/categories")
	public List<Map<String, Object>> categories(@PathVariable UUID outletId) { return catalog.listCategories(outletId); }

	@PostMapping("/outlets/{outletId}/items")
	public Map<String, Object> item(@PathVariable UUID outletId, @RequestBody Map<String, Object> body) {
		if (!(body.get("pricePaise") instanceof Number price)) throw com.restaurant.platform.api.ApiException.bad("VALIDATION", "Price must be a number");
		return catalog.createItem(outletId, UUID.fromString((String) body.get("categoryId")),
				(String) body.get("name"), (String) body.getOrDefault("description", ""), (String) body.get("image"), price.longValue(),
				body.get("taxCodeId") == null ? null : UUID.fromString((String) body.get("taxCodeId")),
				body.get("availableOnQr") == null || Boolean.TRUE.equals(body.get("availableOnQr")), body.get("available") == null || Boolean.TRUE.equals(body.get("available")));
	}

	@PutMapping("/items/{itemId}")
	public Map<String, Object> update(@PathVariable UUID itemId, @RequestBody Map<String, Object> body) {
		if (!(body.get("pricePaise") instanceof Number price)) throw com.restaurant.platform.api.ApiException.bad("VALIDATION", "Price must be a number");
		if (body.get("categoryId") == null) throw com.restaurant.platform.api.ApiException.bad("VALIDATION", "Category is required");
		return catalog.updateItem(itemId, UUID.fromString(String.valueOf(body.get("categoryId"))), body.get("name") == null ? null : String.valueOf(body.get("name")),
				String.valueOf(body.getOrDefault("description", "")), body.get("image") == null ? null : String.valueOf(body.get("image")), price.longValue(),
				body.get("availableOnQr") == null || Boolean.TRUE.equals(body.get("availableOnQr")), body.get("available") == null || Boolean.TRUE.equals(body.get("available")));
	}

	@PatchMapping("/items/{itemId}/availability")
	public Map<String, Object> availability(@PathVariable UUID itemId, @RequestBody Map<String, Boolean> body) {
		if (!body.containsKey("available")) throw com.restaurant.platform.api.ApiException.bad("VALIDATION", "Available is required");
		return catalog.setAvailability(itemId, Boolean.TRUE.equals(body.get("available")));
	}

	@DeleteMapping("/items/{itemId}")
	@ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID itemId) { catalog.deleteItem(itemId); }

	@PostMapping("/outlets/{outletId}/modifiers")
	public Map<String, Object> mod(@PathVariable UUID outletId, @RequestBody Map<String, Object> body) {
		return catalog.createModifier(outletId, (String) body.get("name"), ((Number) body.get("extraPaise")).longValue());
	}

	@GetMapping("/outlets/{outletId}/menu")
	public List<Map<String, Object>> menu(@PathVariable UUID outletId, @RequestParam(defaultValue = "false") boolean qr) {
		return catalog.channelMenu(outletId, qr);
	}
}
