package com.restaurant.order.api;

import com.restaurant.catalog.application.CatalogService;
import com.restaurant.order.application.OrderService;
import com.restaurant.outlet.api.QrLookup;
import com.restaurant.outlet.application.FloorService;
import com.restaurant.platform.api.IdempotencyService;
import com.restaurant.platform.api.TenantContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
	private final OrderService orders;
	private final FloorService floor;
	private final CatalogService catalog;
	private final IdempotencyService idempotency;

	public OrderController(OrderService orders, FloorService floor, CatalogService catalog, IdempotencyService idempotency) {
		this.orders = orders;
		this.floor = floor;
		this.catalog = catalog;
		this.idempotency = idempotency;
	}

	@GetMapping("/public/qr/{token}/menu")
	public List<Map<String, Object>> menu(@PathVariable String token) {
		QrLookup q = floor.requireToken(token);
		if (!q.active()) throw com.restaurant.platform.api.ApiException.gone("QR_ROTATED", "QR rotated");
		com.restaurant.platform.api.TenantContext.set(new com.restaurant.platform.api.TenantPrincipal(
				q.tenantId(), null, List.of(), java.util.Set.of("GUEST"), "table_guest", q.tableId(), null, q.tokenId(), q.outletId()));
		return catalog.channelMenu(q.outletId(), true);
	}

	@PostMapping("/public/qr/{token}/rounds")
	public ResponseEntity<String> guestRound(@PathVariable String token,
			@RequestHeader("Idempotency-Key") String key,
			@RequestBody String raw) {
		return idempotency.run(TenantContext.require().tenantId(), key, raw, () -> {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = read(raw);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
			return json(orders.guestRound(token, items));
		});
	}

	@GetMapping("/public/qr/{token}/order")
	public Map<String, Object> guestOrder(@PathVariable String token) {
		return orders.tableOrder(token);
	}

	@PostMapping("/public/qr/{token}/request-bill")
	public ResponseEntity<String> guestBill(@PathVariable String token,
			@RequestHeader("Idempotency-Key") String key,
			@RequestBody(required = false) String raw) {
		return idempotency.run(TenantContext.require().tenantId(), key, raw == null ? "" : raw, () -> {
			var view = orders.tableOrder(token);
			UUID orderId = UUID.fromString(view.get("id").toString());
			return json(orders.requestBill(orderId, true, 0));
		});
	}

	@PostMapping("/outlets/{outletId}/orders")
	public ResponseEntity<String> counter(@PathVariable UUID outletId,
			@RequestHeader("Idempotency-Key") String key,
			@RequestBody String raw) {
		return idempotency.run(TenantContext.require().tenantId(), key, raw, () -> {
			Map<String, Object> body = read(raw);
			UUID tableId = body.get("tableId") == null ? null : UUID.fromString(body.get("tableId").toString());
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
			return json(orders.counterOrder(outletId, tableId, (String) body.get("channel"), items));
		});
	}

	@GetMapping("/orders/{orderId}")
	public Map<String, Object> get(@PathVariable UUID orderId) {
		return orders.get(orderId);
	}

	@PostMapping("/orders/{orderId}/request-bill")
	public ResponseEntity<String> bill(@PathVariable UUID orderId,
			@RequestHeader("Idempotency-Key") String key,
			@RequestBody(required = false) String raw) {
		return idempotency.run(TenantContext.require().tenantId(), key, raw == null ? "" : raw, () -> {
			Map<String, Object> body = raw == null || raw.isBlank() ? Map.of() : read(raw);
			long disc = body.get("discountPaise") == null ? 0 : ((Number) body.get("discountPaise")).longValue();
			return json(orders.requestBill(orderId, true, disc));
		});
	}

	@PostMapping("/orders/{orderId}/unlock-add")
	public Map<String, Object> unlock(@PathVariable UUID orderId) {
		return orders.unlockAdd(orderId);
	}

	@PostMapping("/orders/{orderId}/cancel")
	public ResponseEntity<String> cancel(@PathVariable UUID orderId, @RequestHeader("Idempotency-Key") String key) {
		return idempotency.run(TenantContext.require().tenantId(), key, orderId.toString(), () -> json(orders.cancel(orderId)));
	}

	@PostMapping("/orders/{orderId}/status")
	public Map<String, Object> patch(@PathVariable UUID orderId, @RequestBody Map<String, String> body) {
		return orders.illegalPatch(orderId, body.get("status"));
	}

	private static ResponseEntity<String> json(Map<String, Object> body) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (var e : body.entrySet()) {
			if (!first) sb.append(',');
			first = false;
			sb.append('"').append(e.getKey()).append("\":");
			Object v = e.getValue();
			if (v == null || v instanceof Number || v instanceof Boolean) sb.append(v);
			else sb.append('"').append(v).append('"');
		}
		sb.append('}');
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(sb.toString());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> read(String raw) {
		return new org.springframework.boot.json.JacksonJsonParser().parseMap(raw);
	}
}
