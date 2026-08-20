package com.restaurant.outlet.api;

import com.restaurant.outlet.application.FloorService;
import com.restaurant.platform.api.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FloorController {
	private final FloorService floor;

	public FloorController(FloorService floor) {
		this.floor = floor;
	}

	@PostMapping("/outlets/{outletId}/areas")
	public Map<String, Object> area(@PathVariable UUID outletId, @RequestBody Map<String, String> body) {
		return floor.createArea(outletId, body.get("name"));
	}

	@PostMapping("/areas/{areaId}/tables")
	public Map<String, Object> table(@PathVariable UUID areaId, @RequestBody Map<String, Object> body) {
		int seats = body.get("seats") == null ? 4 : ((Number) body.get("seats")).intValue();
		return floor.createTable(areaId, String.valueOf(body.get("code")), seats);
	}

	@PostMapping("/tables/{tableId}/rotate-qr")
	public Map<String, Object> rotate(@PathVariable UUID tableId) {
		return floor.rotateQr(tableId);
	}

	@PostMapping("/tables/{tableId}/qr-lock")
	public ResponseEntity<Void> lock(@PathVariable UUID tableId, @RequestBody Map<String, Boolean> body) {
		floor.setQrLocked(tableId, Boolean.TRUE.equals(body.get("locked")));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/tables/{tableId}/clear-table")
	public ResponseEntity<Void> clear(@PathVariable UUID tableId) {
		floor.clearTable(tableId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/public/qr/{token}")
	public Map<String, Object> info(@PathVariable String token) {
		return floor.publicInfo(token);
	}

	@PostMapping("/public/qr/{token}/sessions")
	public Map<String, Object> session(@PathVariable String token, jakarta.servlet.http.HttpServletRequest req) {
		return floor.openSession(token, req.getRemoteAddr());
	}

	@GetMapping("/me")
	public Map<String, Object> me() {
		var p = TenantContext.require();
		return Map.of("tenantId", p.tenantId(), "typ", p.typ(), "roles", p.roles());
	}
}
