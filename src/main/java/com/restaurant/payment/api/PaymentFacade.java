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
import java.util.Locale;
import java.util.Set;

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

	@Transactional(readOnly = true)
	public long successfulAmount(UUID invoiceId) {
		return payments.findByInvoiceId(invoiceId).stream()
				.filter(payment -> "SUCCESS".equals(payment.getStatus()))
				.mapToLong(PaymentEntity::getAmountPaise)
				.sum();
	}

	@Transactional
	public Map<String, Object> record(UUID invoiceId, String method, long tenderedPaise) {
		var p = TenantContext.require();
		if (p.isGuest()) throw ApiException.forbidden("GUEST_PAY", "Guest cannot pay");
		if (!(p.hasRole("OWNER") || p.hasRole("MANAGER") || p.hasRole("CASHIER"))) {
			throw ApiException.forbidden("RBAC", "Cashier role required");
		}
		String normalizedMethod=method==null?"":method.trim().toUpperCase(Locale.ROOT);
		if(!Set.of("CASH","UPI","CARD").contains(normalizedMethod))throw ApiException.bad("PAYMENT_METHOD","Payment method must be CASH, UPI, or CARD");
		if(tenderedPaise<=0)throw ApiException.bad("PAYMENT_AMOUNT","Payment amount must be greater than zero");
		InvoiceEntity inv = billing.lock(invoiceId);
		if("VOID".equals(inv.getStatus()))throw ApiException.conflict("INVOICE_VOID","A void invoice cannot be paid");
		long already = payments.findByInvoiceId(invoiceId).stream()
				.filter(x -> "SUCCESS".equals(x.getStatus()))
				.mapToLong(PaymentEntity::getAmountPaise).sum();
		long remaining = inv.getTotalPaise() - already;
		if(remaining<=0||"PAID".equals(inv.getStatus()))throw ApiException.conflict("INVOICE_PAID","Invoice is already fully paid");
		long applied = Math.min(tenderedPaise, remaining);
		long change = Math.max(0, tenderedPaise - remaining);
		PaymentEntity pay = new PaymentEntity();
		pay.setTenantId(p.tenantId());
		pay.setInvoiceId(invoiceId);
		pay.setMethod(normalizedMethod);
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
							+ ",\"method\":\"" + normalizedMethod + "\"}");
			events.publishEvent(new InvoicePaid(p.tenantId(), inv.getOrderId(), null));
		}
		return Map.of("paymentId", pay.getId(), "invoicePaid", paid, "changePaise", change, "orderId", inv.getOrderId());
	}
}
