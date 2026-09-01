package com.restaurant.billing.api;

import com.restaurant.billing.infrastructure.InvoiceEntity;
import com.restaurant.billing.infrastructure.InvoiceLineEntity;
import com.restaurant.billing.infrastructure.InvoiceLineRepository;
import com.restaurant.billing.infrastructure.InvoiceRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BillingFacade {
	private final InvoiceRepository invoices;
	private final InvoiceLineRepository lines;
	private final JdbcTemplate jdbc;

	public BillingFacade(InvoiceRepository invoices, InvoiceLineRepository lines, JdbcTemplate jdbc) {
		this.invoices = invoices;
		this.lines = lines; this.jdbc = jdbc;
	}

	@Transactional
	public InvoiceEntity generate(UUID outletId, UUID orderId, String channel, long subtotalPaise, long discountPaise,
			int serviceChargeBps, long packagingIfTakeaway, boolean taxInclusive, int taxBps,
			List<Map<String, Object>> lineSnaps) {
		if (invoices.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, "GENERATED").isPresent()) {
			throw ApiException.conflict("INVOICE_EXISTS", "Invoice already exists");
		}
		long service = 0;
		if ("QR_DINE_IN".equals(channel) || "COUNTER_DINE_IN".equals(channel)) {
			service = BigDecimal.valueOf(subtotalPaise - discountPaise)
					.multiply(BigDecimal.valueOf(serviceChargeBps))
					.divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
					.longValue();
		}
		long packaging = "TAKEAWAY".equals(channel) ? packagingIfTakeaway : 0;
		long base = subtotalPaise - discountPaise + service + packaging;
		long tax = taxInclusive ? 0 : BigDecimal.valueOf(base)
				.multiply(BigDecimal.valueOf(taxBps))
				.divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
				.longValue();
		long total = taxInclusive ? base : base + tax;
		InvoiceEntity inv = new InvoiceEntity();
		inv.setTenantId(TenantContext.require().tenantId());
		inv.setOutletId(outletId);
		inv.setOrderId(orderId);
		inv.setSubtotalPaise(subtotalPaise);
		inv.setDiscountPaise(discountPaise);
		inv.setServiceChargePaise(service);
		inv.setPackagingPaise(packaging);
		inv.setTaxPaise(tax);
		inv.setTotalPaise(total);
		inv.setTaxInclusive(taxInclusive);
		inv.setRoundingMode("HALF_UP");
		try { invoices.saveAndFlush(inv); } catch (DataIntegrityViolationException ex) { throw ApiException.conflict("INVOICE_EXISTS", "Invoice already exists"); }
		for (Map<String, Object> snap : lineSnaps) {
			InvoiceLineEntity l = new InvoiceLineEntity();
			l.setTenantId(inv.getTenantId());
			l.setInvoiceId(inv.getId());
			l.setName(String.valueOf(snap.get("name")));
			l.setQty(new BigDecimal(String.valueOf(snap.get("qty"))));
			l.setUnitPaise(((Number) snap.get("unitPaise")).longValue());
			l.setLinePaise(((Number) snap.get("linePaise")).longValue());
			l.setTaxPaise(0);
			lines.save(l);
		}
		return inv;
	}

	@Transactional
	public void voidUnpaidForRevision(UUID orderId) {
		InvoiceEntity inv = invoices.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, "GENERATED")
				.orElseThrow(() -> ApiException.conflict("INVOICE_REVISION", "There is no unpaid generated invoice to revise"));
		Integer count = jdbc.queryForObject("select count(*) from payments where tenant_id=? and invoice_id=? and status='SUCCESS'", Integer.class, TenantContext.require().tenantId(), inv.getId());
		boolean paymentExists = count != null && count > 0;
		if (paymentExists) throw ApiException.conflict("INVOICE_PAYMENT_STARTED", "A bill cannot be changed after payment succeeds");
		inv.setStatus("VOID"); invoices.save(inv);
	}

	@Transactional
	public void markPaid(UUID invoiceId) {
		InvoiceEntity inv = invoices.findById(invoiceId).orElseThrow();
		inv.setStatus("PAID");
		invoices.save(inv);
	}

	public InvoiceEntity lock(UUID invoiceId) { return invoices.findLockedById(invoiceId).orElseThrow(() -> ApiException.notFound("INVOICE", "No invoice")); }

	public InvoiceEntity byOrder(UUID orderId) {
		return invoices.findFirstByOrderIdOrderByCreatedAtDesc(orderId).orElseThrow(() -> ApiException.notFound("INVOICE", "No invoice"));
	}

	public Optional<InvoiceEntity> findByOrder(UUID orderId) {
		return invoices.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
	}

	public InvoiceEntity require(UUID id) {
		return invoices.findById(id).orElseThrow(() -> ApiException.notFound("INVOICE", "No invoice"));
	}
}
