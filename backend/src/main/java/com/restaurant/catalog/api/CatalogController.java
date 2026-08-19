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

	@PostMapping("/outlets/{outletId}/items")
	public Map<String, Object> item(@PathVariable UUID outletId, @RequestBody Map<String, Object> body) {
		return catalog.createItem(outletId, UUID.fromString((String) body.get("categoryId")),
				(String) body.get("name"), ((Number) body.get("pricePaise")).longValue(),
				body.get("taxCodeId") == null ? null : UUID.fromString((String) body.get("taxCodeId")),
				body.get("availableOnQr") == null || Boolean.TRUE.equals(body.get("availableOnQr")));
	}

	@PostMapping("/outlets/{outletId}/modifiers")
	public Map<String, Object> mod(@PathVariable UUID outletId, @RequestBody Map<String, Object> body) {
		return catalog.createModifier(outletId, (String) body.get("name"), ((Number) body.get("extraPaise")).longValue());
	}

	@GetMapping("/outlets/{outletId}/menu")
	public List<Map<String, Object>> menu(@PathVariable UUID outletId, @RequestParam(defaultValue = "false") boolean qr) {
		return catalog.channelMenu(outletId, qr);
	}
}
