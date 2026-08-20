package com.restaurant.identity.infrastructure;
import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(name="permissions")
public class PermissionEntity { @Id private UUID id; private UUID tenantId; private String code; private String description; private String category; public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public String getCode(){return code;} public String getDescription(){return description;} public String getCategory(){return category;} }
