package com.restaurant.payment.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
public class PaymentTxEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID paymentId;
	private String status;
	private long amountPaise;
	private String note;
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
	public void setStatus(String status) { this.status = status; }
	public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
	public void setNote(String note) { this.note = note; }
}
