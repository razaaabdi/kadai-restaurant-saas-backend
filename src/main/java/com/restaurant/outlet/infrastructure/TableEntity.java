package com.restaurant.outlet.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "tables")
public class TableEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private UUID areaId;
	private String code;
	private int seats = 4;
	private String status = "FREE";
	private boolean qrLocked;
	private boolean deleted;
	@Version private long version;

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public UUID getOutletId() { return outletId; }
	public void setOutletId(UUID outletId) { this.outletId = outletId; }
	public UUID getAreaId() { return areaId; }
	public void setAreaId(UUID areaId) { this.areaId = areaId; }
	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }
	public int getSeats() { return seats; }
	public void setSeats(int seats) { this.seats = seats; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public boolean isQrLocked() { return qrLocked; }
	public void setQrLocked(boolean qrLocked) { this.qrLocked = qrLocked; }
	public long getVersion() { return version; }
	public boolean isDeleted() { return deleted; }
	public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
