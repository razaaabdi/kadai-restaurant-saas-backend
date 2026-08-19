package com.restaurant.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {
	@Id
	private UUID id = UUID.randomUUID();
	private UUID tenantId;
	private String email;
	private String passwordHash;
	private String name;
	private String status = "ACTIVE";

	public UUID getId() { return id; }
	public UUID getTenantId() { return tenantId; }
	public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getStatus() { return status; }
}
