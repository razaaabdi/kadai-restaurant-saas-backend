package com.restaurant.organization.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tenants")
public class TenantEntity {
	@Id private UUID id = UUID.randomUUID();
	private String name;
	private String slug;
	private String status = "ACTIVE";
	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public void setName(String name) { this.name = name; }
	public String getSlug() { return slug; }
	public void setSlug(String slug) { this.slug = slug; }
}
