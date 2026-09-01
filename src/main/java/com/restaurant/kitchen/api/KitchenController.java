package com.restaurant.kitchen.api;

import com.restaurant.kitchen.application.KitchenService;
import com.restaurant.kitchen.infrastructure.KotEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class KitchenController {
	private final KitchenService kitchen;

	public KitchenController(KitchenService kitchen) {
		this.kitchen = kitchen;
	}

	@GetMapping("/orders/{orderId}/kots")
	public List<Map<String, Object>> list(@PathVariable UUID orderId) {
		return kitchen.byOrder(orderId).stream().map(k -> {
			Map<String, Object> row = new java.util.LinkedHashMap<>();
			row.put("id", k.getId());
			row.put("roundId", k.getRoundId());
			row.put("kotNumber", k.getKotNumber());
			row.put("status", k.getStatus());
			row.put("print", kitchen.printStatus(k.getId()));
			return row;
		}).toList();
	}

	@PostMapping("/kots/{kotId}/start-prep")
	public ResponseEntity<Void> prep(@PathVariable UUID kotId) {
		kitchen.startPrep(kotId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/kots/{kotId}/accept")
	public ResponseEntity<Void> accept(@PathVariable UUID kotId) {
		kitchen.accept(kotId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/kots/{kotId}/items/{itemId}/mark-ready")
	public ResponseEntity<Void> itemReady(@PathVariable UUID kotId, @PathVariable UUID itemId) {
		kitchen.markItemReady(kotId, itemId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/kots/{kotId}/mark-ready")
	public ResponseEntity<Void> ready(@PathVariable UUID kotId) {
		kitchen.markReady(kotId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/kots/{kotId}/reprint")
	public Map<String, Object> reprint(@PathVariable UUID kotId, @RequestBody Map<String, String> body) {
		return kitchen.reprint(kotId, body.get("reason"));
	}

	@PostMapping("/kots/{kotId}/retry-print")
	public Map<String, Object> retry(@PathVariable UUID kotId) {
		return kitchen.retryPrint(kotId);
	}
}
