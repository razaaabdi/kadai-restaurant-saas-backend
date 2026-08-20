package com.restaurant.configuration.infrastructure;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="config_entries")
public class ConfigEntryEntity {
 @Id private UUID id=UUID.randomUUID(); private UUID tenantId; private String scope; private UUID scopeId; private String key; @Column(columnDefinition="text") private String value; @Version private long version; private Instant updatedAt=Instant.now(); private UUID updatedBy;
 public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public void setTenantId(UUID v){tenantId=v;} public String getScope(){return scope;} public void setScope(String v){scope=v;} public UUID getScopeId(){return scopeId;} public void setScopeId(UUID v){scopeId=v;} public String getKey(){return key;} public void setKey(String v){key=v;} public String getValue(){return value;} public void setValue(String v){value=v;} public long getVersion(){return version;} public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;} public void setUpdatedBy(UUID v){updatedBy=v;}
}
