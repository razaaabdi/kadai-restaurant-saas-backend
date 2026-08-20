package com.restaurant.reporting.application;

import com.restaurant.platform.api.AppProperties;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import com.restaurant.platform.infrastructure.OutboxEntity;
import com.restaurant.platform.infrastructure.OutboxRepository;
import com.restaurant.reporting.infrastructure.DailySalesEntity;
import com.restaurant.reporting.infrastructure.DailySalesId;
import com.restaurant.reporting.infrastructure.DailySalesRepository;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OutboxPoller {
	private final OutboxRepository outbox;
	private final DailySalesRepository sales;
	private final AppProperties props;

	public OutboxPoller(OutboxRepository outbox, DailySalesRepository sales, AppProperties props) {
		this.outbox = outbox;
		this.sales = sales;
		this.props = props;
	}

	@Scheduled(fixedDelayString = "${app.outbox.poll-ms:2000}")
	@Transactional
	public void poll() {
		List<OutboxEntity> batch = outbox.findTop50ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc("PENDING", Instant.now());
		for (OutboxEntity e : batch) {
			try {
				TenantContext.set(new TenantPrincipal(e.getTenantId(), null, List.of(), java.util.Set.of(), "staff",
						null, null, null, null));
				if ("OrderPaid".equals(e.getType())) {
					applyPaid(e);
				}
				e.setStatus("DONE");
			} catch (Exception ex) {
				int n = e.getRetryCount() + 1;
				e.setRetryCount(n);
				e.setLastError(ex.getMessage());
				if (n >= props.getOutbox().getMaxAttempts()) {
					e.setStatus("DEAD");
				} else {
					e.setNextAttemptAt(Instant.now().plusSeconds((long) Math.pow(2, n)));
				}
			}
			outbox.save(e);
		}
	}

	@Transactional
	public void drainNow() {
		poll();
	}

	private void applyPaid(OutboxEntity e) {
		Map<String, Object> p = new JacksonJsonParser().parseMap(e.getPayload());
		UUID tenant = e.getTenantId();
		UUID outlet = UUID.fromString(String.valueOf(p.get("outletId")));
		LocalDate day = LocalDate.now(ZoneOffset.UTC);
		DailySalesId id = new DailySalesId();
		DailySalesEntity row = sales.findById(new DailySalesId(tenant, outlet, day)).orElseGet(() -> {
			DailySalesEntity n = new DailySalesEntity();
			n.setTenantId(tenant);
			n.setOutletId(outlet);
			n.setBusinessDate(day);
			return n;
		});
		row.setOrdersCount(row.getOrdersCount() + 1);
		row.setGmvPaise(row.getGmvPaise() + asLong(p.get("gmv")));
		row.setDiscountPaise(asLong(p.get("discount")));
		row.setTaxPaise(asLong(p.get("tax")));
		row.addMethod(String.valueOf(p.get("method")), asLong(p.get("gmv")));
		sales.save(row);
	}

	private static long asLong(Object o) {
		if (o instanceof Number n) return n.longValue();
		return o == null ? 0 : Long.parseLong(o.toString());
	}
}
