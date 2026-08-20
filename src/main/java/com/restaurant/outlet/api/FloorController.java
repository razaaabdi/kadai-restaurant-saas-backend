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

	@GetMapping("/outlets/{outletId}/tables")
	public java.util.List<Map<String, Object>> tables(@PathVariable UUID outletId) { return floor.listFloor(outletId); }

	@GetMapping("/outlets/{outletId}/areas")
	public java.util.List<Map<String, Object>> areas(@PathVariable UUID outletId) {
		return floor.listAreas(outletId).stream().map(area -> Map.<String, Object>of("id", area.getId(), "name", area.getName(), "outletId", area.getOutletId())).toList();
	}

	@PutMapping("/tables/{tableId}")
	public Map<String, Object> update(@PathVariable UUID tableId, @RequestHeader(value = "If-Match", required = false) Long version,
			@RequestBody Map<String, Object> body) {
		if (!(body.get("seats") instanceof Number seats)) throw com.restaurant.platform.api.ApiException.bad("VALIDATION", "Seats must be a number");
		return floor.updateTable(tableId, body.get("code") == null ? null : body.get("code").toString(), seats.intValue(),
				body.get("status") == null ? "FREE" : body.get("status").toString(), version);
	}

	@DeleteMapping("/tables/{tableId}")
	public ResponseEntity<Void> delete(@PathVariable UUID tableId, @RequestHeader(value = "If-Match", required = false) Long version) {
		floor.removeTable(tableId, version); return ResponseEntity.noContent().build();
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
