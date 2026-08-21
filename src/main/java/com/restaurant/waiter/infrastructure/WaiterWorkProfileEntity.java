package com.restaurant.waiter.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waiter_work_profiles")
public class WaiterWorkProfileEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId; private UUID outletId; private UUID waiterId;
	private String manualStatus = "ONLINE"; private int capacity = 5;
	@Version private long version;
	private Instant createdAt = Instant.now(); private Instant updatedAt = Instant.now();
	public UUID getId(){return id;} public UUID getOutletId(){return outletId;} public UUID getWaiterId(){return waiterId;} public String getManualStatus(){return manualStatus;} public int getCapacity(){return capacity;} public long getVersion(){return version;}
	public void setTenantId(UUID v){tenantId=v;} public void setOutletId(UUID v){outletId=v;} public void setWaiterId(UUID v){waiterId=v;} public void setManualStatus(String v){manualStatus=v;updatedAt=Instant.now();} public void setCapacity(int v){capacity=v;updatedAt=Instant.now();}
}
