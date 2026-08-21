package com.restaurant.waiter.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waiter_assignments")
public class WaiterAssignmentEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private UUID tableId;
	private UUID orderId;
	private UUID waiterId;
	private String status;
	private UUID assignedBy;
	private Instant assignedAt = Instant.now();
	private Instant acceptedAt;
	private Instant releasedAt;
	private UUID transferToWaiterId;
	private UUID transferRequestedBy;
	private Instant transferRequestedAt;
	private String transferReason;
	private UUID previousAssignmentId;
	@Version private long version;
	private Instant createdAt = Instant.now();
	private Instant updatedAt = Instant.now();
	public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getOutletId(){return outletId;} public UUID getTableId(){return tableId;} public UUID getOrderId(){return orderId;} public UUID getWaiterId(){return waiterId;} public String getStatus(){return status;} public UUID getAssignedBy(){return assignedBy;} public Instant getAssignedAt(){return assignedAt;} public Instant getAcceptedAt(){return acceptedAt;} public Instant getReleasedAt(){return releasedAt;} public UUID getTransferToWaiterId(){return transferToWaiterId;} public UUID getTransferRequestedBy(){return transferRequestedBy;} public Instant getTransferRequestedAt(){return transferRequestedAt;} public String getTransferReason(){return transferReason;} public UUID getPreviousAssignmentId(){return previousAssignmentId;} public long getVersion(){return version;} public Instant getUpdatedAt(){return updatedAt;}
	public void setTenantId(UUID v){tenantId=v;} public void setOutletId(UUID v){outletId=v;} public void setTableId(UUID v){tableId=v;} public void setOrderId(UUID v){orderId=v;} public void setWaiterId(UUID v){waiterId=v;} public void setStatus(String v){status=v;touch();} public void setAssignedBy(UUID v){assignedBy=v;} public void setAcceptedAt(Instant v){acceptedAt=v;touch();} public void setReleasedAt(Instant v){releasedAt=v;touch();} public void setTransferToWaiterId(UUID v){transferToWaiterId=v;touch();} public void setTransferRequestedBy(UUID v){transferRequestedBy=v;touch();} public void setTransferRequestedAt(Instant v){transferRequestedAt=v;touch();} public void setTransferReason(String v){transferReason=v;touch();} public void setPreviousAssignmentId(UUID v){previousAssignmentId=v;} private void touch(){updatedAt=Instant.now();}
}
