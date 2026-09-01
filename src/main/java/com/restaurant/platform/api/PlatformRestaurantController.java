package com.restaurant.platform.api;

import com.restaurant.platform.application.PlatformReadService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformRestaurantController {
	private final PlatformRestaurantCommands commands;
	private final PlatformReadService reads;

	public PlatformRestaurantController(PlatformRestaurantCommands commands, PlatformReadService reads) {
		this.commands = commands;
		this.reads = reads;
	}

	@PostMapping("/restaurants")
	public Map<String, Object> create(@RequestBody Map<String, Object> body) {
		Map<String,Object> created=commands.create(actor(), body);
		Map<String,Object> response=new java.util.LinkedHashMap<>(reads.restaurant((UUID)created.get("tenantId")));
		response.put("ownerSetupToken",created.get("ownerSetupToken"));
		return response;
	}

	@PostMapping("/restaurants/{id}/{action:activate|suspend|disable}")
	public Map<String, Object> status(@PathVariable UUID id, @PathVariable String action,
			@RequestHeader("If-Match") long version, @RequestBody Map<String, Object> body) {
		commands.changeTenantStatus(actor(), id, action, text(body, "reason"), version);
		return reads.restaurant(id);
	}

	@PostMapping("/restaurants/{id}/outlets")
	public Map<String, Object> outlet(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return commands.addOutlet(actor(), id, body);
	}

	@PostMapping("/outlets/{id}/{action:activate|suspend}")
	public Map<String, Object> outletStatus(@PathVariable UUID id, @PathVariable String action,
			@RequestHeader("If-Match") long version, @RequestBody Map<String, Object> body) {
		return commands.changeOutletStatus(actor(), id, action, text(body, "reason"), version);
	}

	@PostMapping("/restaurants/{id}/subscription/renew")
	public Map<String, Object> renew(@PathVariable UUID id, @RequestHeader("If-Match") long version,
			@RequestBody Map<String, Object> body) {
		commands.renew(actor(), id, ((Number) body.getOrDefault("months", 12)).intValue(), text(body, "reason"), version);
		return reads.restaurant(id);
	}

	@PostMapping("/restaurants/{id}/subscription/change-plan")
	public Map<String, Object> changePlan(@PathVariable UUID id, @RequestHeader("If-Match") long version,
			@RequestBody Map<String, Object> body) {
		UUID planId;
		try { planId = UUID.fromString(text(body, "planId")); }
		catch (Exception e) { throw ApiException.bad("PLAN", "A valid plan is required"); }
		commands.changePlan(actor(), id, planId, text(body, "reason"), version);
		return reads.restaurant(id);
	}

	private UUID actor() {
		var principal = TenantContext.get();
		if (principal == null || !"platform".equals(principal.typ()) || principal.userId() == null)
			throw ApiException.unauthorized("Platform administrator required");
		return principal.userId();
	}

	private static String text(Map<String, Object> body, String key) {
		Object value = body.get(key);
		return value == null ? "" : String.valueOf(value).trim();
	}
}
