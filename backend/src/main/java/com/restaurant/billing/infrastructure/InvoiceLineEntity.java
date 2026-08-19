package com.restaurant.billing.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLineEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID invoiceId;
	private String name;
	private BigDecimal qty;
	private long unitPaise;
	private long linePaise;
	private long taxPaise;
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
	public void setName(String name) { this.name = name; }
	public void setQty(BigDecimal qty) { this.qty = qty; }
	public void setUnitPaise(long unitPaise) { this.unitPaise = unitPaise; }
	public void setLinePaise(long linePaise) { this.linePaise = linePaise; }
	public void setTaxPaise(long taxPaise) { this.taxPaise = taxPaise; }
}
