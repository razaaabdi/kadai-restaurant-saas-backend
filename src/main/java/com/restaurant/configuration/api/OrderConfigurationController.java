package com.restaurant.configuration.api;

import com.restaurant.configuration.application.OrderConfigurationService;
import com.restaurant.platform.api.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrderConfigurationController {
	private final OrderConfigurationService configuration;

	public OrderConfigurationController(OrderConfigurationService configuration) {
		this.configuration = configuration;
	}

	@GetMapping("/order-configuration")
	public OrderConfigurationResponse get(@RequestParam(required = false) UUID outletId) {
		return configuration.effective(outletId);
	}

	@PutMapping("/order-configuration")
	public ResponseEntity<OrderConfigurationResponse> tenant(
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String version,
			@RequestBody Map<String, Object> body) {
		return ResponseEntity.ok(configuration.updateTenant(body, version(version)));
	}

	@PutMapping("/outlets/{outletId}/order-configuration")
	public ResponseEntity<OrderConfigurationResponse> outlet(@PathVariable UUID outletId,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String version,
			@RequestBody Map<String, Object> body) {
		return ResponseEntity.ok(configuration.updateOutlet(outletId, body, version(version)));
	}

	private static long version(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED",
					"If-Match with the current numeric scope version is required");
		}
		try {
			return Long.parseLong(raw.strip().replace(String.valueOf((char) 34), ""));
		} catch (NumberFormatException ex) {
			throw ApiException.bad("IF_MATCH", "If-Match must contain the current numeric scope version");
		}
	}
}
