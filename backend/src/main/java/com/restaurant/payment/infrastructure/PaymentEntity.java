package com.restaurant.payment.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID invoiceId;
	private String method;
	private long amountPaise;
	private long changePaise;
	private String status = "SUCCESS";
	public UUID getId() { return id; }
	public UUID getInvoiceId() { return invoiceId; }
	public String getMethod() { return method; }
	public long getAmountPaise() { return amountPaise; }
	public String getStatus() { return status; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
	public void setMethod(String method) { this.method = method; }
	public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
	public void setChangePaise(long changePaise) { this.changePaise = changePaise; }
	public void setStatus(String status) { this.status = status; }
	public UUID getTenantId() { return tenantId; }
}
