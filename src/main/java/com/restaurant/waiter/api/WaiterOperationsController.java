package com.restaurant.waiter.api;

import com.restaurant.waiter.application.WaiterOperationsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outlets/{outletId}/waiter")
public class WaiterOperationsController {
	private final WaiterOperationsService waiter;
	public WaiterOperationsController(WaiterOperationsService waiter) { this.waiter = waiter; }

	@GetMapping("/orders")
	public List<Map<String,Object>> active(@PathVariable UUID outletId, @RequestParam(defaultValue = "false") boolean mineOnly) { return waiter.activeOrders(outletId, mineOnly); }

	@GetMapping("/orders/{orderId}")
	public Map<String,Object> detail(@PathVariable UUID outletId, @PathVariable UUID orderId) { return waiter.detail(outletId, orderId); }

	@PostMapping("/orders/{orderId}/items/{itemId}/pickup")
	public Map<String,Object> pickup(@PathVariable UUID outletId, @PathVariable UUID orderId, @PathVariable UUID itemId) { return waiter.pickup(outletId, orderId, itemId); }

	@PostMapping("/orders/{orderId}/items/{itemId}/serve")
	public Map<String,Object> serve(@PathVariable UUID outletId, @PathVariable UUID orderId, @PathVariable UUID itemId) { return waiter.serve(outletId, orderId, itemId); }

	@PostMapping("/orders/{orderId}/items/bulk-transition")
	public Map<String,Object> bulkTransition(@PathVariable UUID outletId,@PathVariable UUID orderId,@RequestBody Map<String,Object> body){
		Object raw=body.get("itemIds");if(!(raw instanceof List<?> values))throw com.restaurant.platform.api.ApiException.bad("ITEM_IDS","itemIds must be an array");
		List<UUID> ids=values.stream().map(value->{try{return UUID.fromString(String.valueOf(value));}catch(Exception e){throw com.restaurant.platform.api.ApiException.bad("ITEM_ID","Every itemId must be a valid UUID");}}).toList();
		return waiter.bulkTransition(outletId,orderId,String.valueOf(body.get("action")),String.valueOf(body.get("expectedSourceStatus")),ids);
	}

	@PostMapping("/orders/{orderId}/request-bill")
	public Map<String,Object> requestBill(@PathVariable UUID outletId, @PathVariable UUID orderId) { return waiter.requestBill(outletId, orderId); }

	@PostMapping("/orders/{orderId}/invoice")
	public Map<String,Object> invoice(@PathVariable UUID outletId, @PathVariable UUID orderId, @RequestBody(required = false) Map<String,Object> body) { long discount = body == null || !(body.get("discountPaise") instanceof Number n) ? 0 : n.longValue(); return waiter.generateInvoice(outletId, orderId, discount); }

	@GetMapping("/notifications")
	public List<Map<String,Object>> notifications(@PathVariable UUID outletId) { return waiter.notifications(outletId); }

	@PostMapping("/notifications/{notificationId}/acknowledge")
	public ResponseEntity<Void> acknowledge(@PathVariable UUID outletId, @PathVariable UUID notificationId) { waiter.acknowledge(outletId, notificationId); return ResponseEntity.noContent().build(); }

	@PostMapping("/notifications/acknowledge-all")
	public Map<String,Object> acknowledgeAll(@PathVariable UUID outletId){return waiter.acknowledgeAll(outletId);}
}
