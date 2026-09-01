package com.restaurant.reporting.api;

import com.restaurant.reporting.application.OutboxPoller;
import com.restaurant.reporting.application.ReportingService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReportingController {
	private final ReportingService reporting;
	private final OutboxPoller poller;

	public ReportingController(ReportingService reporting, OutboxPoller poller) {
		this.reporting = reporting;
		this.poller = poller;
	}

	@PostMapping("/outbox/drain")
	public Map<String, String> drain() {
		poller.drainNow();
		return Map.of("status", "ok");
	}

	@GetMapping("/outlets/{outletId}/daily-sales")
	public Map<String, Object> daily(@PathVariable UUID outletId, @RequestParam LocalDate date) {
		return reporting.daily(outletId, date);
	}

	@GetMapping("/outlets/{outletId}/dashboard")
	public Map<String, Object> dashboard(@PathVariable UUID outletId, @RequestParam LocalDate date) { return reporting.dashboard(outletId, date); }
	@GetMapping("/outlets/{outletId}/reports/sales")
	public Map<String, Object> sales(@PathVariable UUID outletId, @RequestParam LocalDate from, @RequestParam LocalDate to) { return reporting.salesReport(outletId, from, to); }
	@GetMapping("/outlets/{outletId}/reports/top-items")
	public java.util.List<Map<String, Object>> topItems(@PathVariable UUID outletId, @RequestParam LocalDate from, @RequestParam LocalDate to) { return reporting.topItems(outletId, from, to); }
	@GetMapping("/outlets/{outletId}/reports/payment-mix")
	public java.util.List<Map<String, Object>> payments(@PathVariable UUID outletId, @RequestParam LocalDate from, @RequestParam LocalDate to) { return reporting.paymentMix(outletId, from, to); }
	@GetMapping("/outlets/{outletId}/alerts")
	public java.util.List<Map<String, Object>> alerts(@PathVariable UUID outletId) { return reporting.alerts(outletId); }
	@GetMapping("/audit-log")
	public java.util.List<Map<String, Object>> activity(@RequestParam(defaultValue = "30") int limit) { return reporting.activity(limit); }
}
