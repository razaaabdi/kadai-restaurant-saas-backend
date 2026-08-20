package com.restaurant.payment.api;

import com.restaurant.billing.api.BillingFacade;
import com.restaurant.billing.infrastructure.InvoiceEntity;
import com.restaurant.payment.infrastructure.PaymentEntity;
import com.restaurant.payment.infrastructure.PaymentRepository;
import com.restaurant.payment.infrastructure.PaymentTxEntity;
import com.restaurant.payment.infrastructure.PaymentTxRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.InvoicePaid;
import com.restaurant.platform.api.OutboxPublisher;
import com.restaurant.platform.api.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PaymentFacade {
	private final PaymentRepository payments;
	private final PaymentTxRepository txs;
	private final BillingFacade billing;
	private final OutboxPublisher outbox;
	private final ApplicationEventPublisher events;

	public PaymentFacade(PaymentRepository payments, PaymentTxRepository txs, BillingFacade billing,
			OutboxPublisher outbox, ApplicationEventPublisher events) {
		this.payments = payments;
		this.txs = txs;
		this.billing = billing;
		this.outbox = outbox;
		this.events = events;
	}

	@Transactional
	public Map<String, Object> record(UUID invoiceId, String method, long tenderedPaise) {
		var p = TenantContext.require();
		if (p.isGuest()) throw ApiException.forbidden("GUEST_PAY", "Guest cannot pay");
		if (!(p.hasRole("OWNER") || p.hasRole("MANAGER") || p.hasRole("CASHIER"))) {
			throw ApiException.forbidden("RBAC", "Cashier role required");
		}
		InvoiceEntity inv = billing.require(invoiceId);
		long already = payments.findByInvoiceId(invoiceId).stream()
				.filter(x -> "SUCCESS".equals(x.getStatus()))
				.mapToLong(PaymentEntity::getAmountPaise).sum();
		long remaining = inv.getTotalPaise() - already;
		long applied = Math.min(tenderedPaise, remaining);
		long change = Math.max(0, tenderedPaise - remaining);
		PaymentEntity pay = new PaymentEntity();
		pay.setTenantId(p.tenantId());
		pay.setInvoiceId(invoiceId);
		pay.setMethod(method);
		pay.setAmountPaise(inv.getTotalPaise() == 0 ? 0 : applied);
		pay.setChangePaise(change);
		pay.setStatus("SUCCESS");
		payments.save(pay);
		PaymentTxEntity tx = new PaymentTxEntity();
		tx.setTenantId(p.tenantId());
		tx.setPaymentId(pay.getId());
		tx.setStatus("SUCCESS");
		tx.setAmountPaise(pay.getAmountPaise());
		txs.save(tx);
		long sum = payments.findByInvoiceId(invoiceId).stream()
				.filter(x -> "SUCCESS".equals(x.getStatus()))
				.mapToLong(PaymentEntity::getAmountPaise).sum();
		boolean paid = inv.getTotalPaise() == 0 || sum >= inv.getTotalPaise();
		if (paid) {
			billing.markPaid(invoiceId);
			outbox.publish(p.tenantId(), "OrderPaid",
					"{\"invoiceId\":\"" + invoiceId + "\",\"orderId\":\"" + inv.getOrderId()
							+ "\",\"outletId\":\"" + inv.getOutletId()
							+ "\",\"gmv\":" + inv.getTotalPaise()
							+ ",\"discount\":" + inv.getDiscountPaise()
							+ ",\"tax\":" + inv.getTaxPaise()
							+ ",\"method\":\"" + method + "\"}");
			events.publishEvent(new InvoicePaid(p.tenantId(), inv.getOrderId(), null));
		}
		return Map.of("paymentId", pay.getId(), "invoicePaid", paid, "changePaise", change, "orderId", inv.getOrderId());
	}
}
