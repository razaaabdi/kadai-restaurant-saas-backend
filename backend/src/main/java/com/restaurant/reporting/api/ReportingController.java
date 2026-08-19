package com.restaurant.reporting.api;

import com.restaurant.reporting.application.OutboxPoller;
import com.restaurant.reporting.infrastructure.DailySalesEntity;
import com.restaurant.reporting.infrastructure.DailySalesId;
import com.restaurant.reporting.infrastructure.DailySalesRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReportingController {
	private final DailySalesRepository sales;
	private final OutboxPoller poller;

	public ReportingController(DailySalesRepository sales, OutboxPoller poller) {
		this.sales = sales;
		this.poller = poller;
	}

	@PostMapping("/outbox/drain")
	public Map<String, String> drain() {
		poller.drainNow();
		return Map.of("status", "ok");
	}

	@GetMapping("/outlets/{outletId}/daily-sales")
	public Map<String, Object> daily(@PathVariable UUID outletId, @RequestParam LocalDate date) {
		var p = com.restaurant.platform.api.TenantContext.require();
		DailySalesEntity row = sales.findById(new DailySalesId(p.tenantId(), outletId, date)).orElse(null);
		if (row == null) return Map.of("ordersCount", 0, "gmvPaise", 0);
		return Map.of("ordersCount", row.getOrdersCount(), "gmvPaise", row.getGmvPaise(),
				"cashPaise", row.getCashPaise(), "upiPaise", row.getUpiPaise());
	}
}
