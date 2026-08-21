package com.restaurant.waiter.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waiter_notifications")
public class WaiterNotificationEntity {
	@Id private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private UUID outletId;
	private UUID recipientUserId;
	private String eventType;
	private UUID orderId;
	private UUID tableId;
	private UUID kotId;
	private String relatedItemIds;
	private String message;
	private String destination;
	private boolean acknowledged;
	private Instant acknowledgedAt;
	private Instant createdAt = Instant.now();
	private String dedupeKey;
	private UUID assignmentId;
	private Instant readAt;
	public UUID getId() { return id; }
	public UUID getRecipientUserId() { return recipientUserId; }
	public UUID getOutletId() { return outletId; }
	public String getEventType() { return eventType; }
	public UUID getOrderId() { return orderId; }
	public UUID getTableId() { return tableId; }
	public UUID getKotId() { return kotId; }
	public String getRelatedItemIds() { return relatedItemIds; }
	public String getMessage() { return message; }
	public String getDestination() { return destination; }
	public boolean isAcknowledged() { return acknowledged; }
	public Instant getCreatedAt() { return createdAt; }
	public UUID getAssignmentId() { return assignmentId; }
	public Instant getReadAt() { return readAt; }
	public void setTenantId(UUID value) { tenantId = value; }
	public void setOutletId(UUID value) { outletId = value; }
	public void setRecipientUserId(UUID value) { recipientUserId = value; }
	public void setEventType(String value) { eventType = value; }
	public void setOrderId(UUID value) { orderId = value; }
	public void setTableId(UUID value) { tableId = value; }
	public void setKotId(UUID value) { kotId = value; }
	public void setRelatedItemIds(String value) { relatedItemIds = value; }
	public void setMessage(String value) { message = value; }
	public void setDestination(String value) { destination = value; }
	public void setAcknowledged(boolean value) { acknowledged = value; }
	public void setAcknowledgedAt(Instant value) { acknowledgedAt = value; }
	public void setDedupeKey(String value) { dedupeKey = value; }
	public void setAssignmentId(UUID value) { assignmentId = value; }
	public void setReadAt(Instant value) { readAt = value; }
}
