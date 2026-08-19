package com.restaurant.reporting.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "outlet_daily_sales")
@IdClass(DailySalesId.class)
public class DailySalesEntity {
	@Id private UUID tenantId;
	@Id private UUID outletId;
	@Id private LocalDate businessDate;
	private int ordersCount;
	private long gmvPaise;
	private long discountPaise;
	private long taxPaise;
	private long refundPaise;
	private long cashPaise;
	private long upiPaise;
	private long cardPaise;
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public UUID getOutletId() { return outletId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public LocalDate getBusinessDate() { return businessDate; }
	public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
	public int getOrdersCount() { return ordersCount; }
	public void setOrdersCount(int ordersCount) { this.ordersCount = ordersCount; }
	public long getGmvPaise() { return gmvPaise; }
	public void setGmvPaise(long gmvPaise) { this.gmvPaise = gmvPaise; }
	public void setDiscountPaise(long discountPaise) { this.discountPaise = discountPaise; }
	public void setTaxPaise(long taxPaise) { this.taxPaise = taxPaise; }
	public void addMethod(String method, long paise) {
		if ("CASH".equalsIgnoreCase(method)) cashPaise += paise;
		else if ("UPI".equalsIgnoreCase(method)) upiPaise += paise;
		else cardPaise += paise;
	}
	public long getCashPaise() { return cashPaise; }
	public long getUpiPaise() { return upiPaise; }
}
