package com.restaurant.outlet.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "outlets")
public class OutletEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID brandId;
	private String name;
	private String slug;
	private String timezone = "Asia/Kolkata";
	private boolean allowNegativeStock = true;
	private boolean qrOrderingEnabled = true;
	private boolean qrAutoConfirm = true;
	private boolean qrGuestCanRequestBill = true;
	private boolean kotEnabled = true;
	private boolean takeawayEnabled = true;
	private boolean unlockAddBeforeBill = true;
	private long maxOpenAmountPaise = 5_000_000;
	private int serviceChargeBps;
	private long packagingChargePaise;
	private boolean taxInclusive;
	private String roundingMode = "HALF_UP";

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public UUID getBrandId() { return brandId; }
	public void setBrandId(UUID brandId) { this.brandId = brandId; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public void setSlug(String slug) { this.slug = slug; }
	public boolean isAllowNegativeStock() { return allowNegativeStock; }
	public boolean isQrOrderingEnabled() { return qrOrderingEnabled; }
	public boolean isQrAutoConfirm() { return qrAutoConfirm; }
	public boolean isQrGuestCanRequestBill() { return qrGuestCanRequestBill; }
	public boolean isUnlockAddBeforeBill() { return unlockAddBeforeBill; }
	public long getMaxOpenAmountPaise() { return maxOpenAmountPaise; }
	public int getServiceChargeBps() { return serviceChargeBps; }
	public long getPackagingChargePaise() { return packagingChargePaise; }
	public boolean isTaxInclusive() { return taxInclusive; }
	public String getRoundingMode() { return roundingMode; }
	public void setMaxOpenAmountPaise(long maxOpenAmountPaise) { this.maxOpenAmountPaise = maxOpenAmountPaise; }
	public void setAllowNegativeStock(boolean allowNegativeStock) { this.allowNegativeStock = allowNegativeStock; }
}
