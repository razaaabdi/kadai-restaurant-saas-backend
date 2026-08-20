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
		return kitchen.byOrder(orderId).stream().map(k -> Map.<String, Object>of(
				"id", k.getId(),
				"roundId", k.getRoundId(),
				"kotNumber", k.getKotNumber(),
				"status", k.getStatus()
		)).toList();
	}

	@PostMapping("/kots/{kotId}/start-prep")
	public ResponseEntity<Void> prep(@PathVariable UUID kotId) {
		kitchen.startPrep(kotId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/kots/{kotId}/mark-ready")
	public ResponseEntity<Void> ready(@PathVariable UUID kotId) {
		kitchen.markReady(kotId);
		return ResponseEntity.noContent().build();
	}
}
