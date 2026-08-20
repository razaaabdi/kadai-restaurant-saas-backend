package com.restaurant.identity.infrastructure;
import jakarta.persistence.*; import java.io.Serializable; import java.util.*;
@Entity @Table(name="role_permissions") @IdClass(RolePermissionId.class)
public class RolePermissionEntity { private UUID tenantId; @Id private UUID roleId; @Id private UUID permissionId; public UUID getTenantId(){return tenantId;} public UUID getRoleId(){return roleId;} public UUID getPermissionId(){return permissionId;} }
class RolePermissionId implements Serializable { private UUID roleId; private UUID permissionId; public boolean equals(Object o){return o instanceof RolePermissionId x&&Objects.equals(roleId,x.roleId)&&Objects.equals(permissionId,x.permissionId);} public int hashCode(){return Objects.hash(roleId,permissionId);} }
