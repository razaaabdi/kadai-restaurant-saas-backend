package com.restaurant.identity.api;

import com.restaurant.identity.infrastructure.RoleEntity;
import com.restaurant.identity.infrastructure.RoleRepository;
import com.restaurant.identity.infrastructure.UserEntity;
import com.restaurant.identity.infrastructure.UserOutletEntity;
import com.restaurant.identity.infrastructure.UserOutletRepository;
import com.restaurant.identity.infrastructure.UserRepository;
import com.restaurant.identity.infrastructure.UserRoleEntity;
import com.restaurant.identity.infrastructure.UserRoleRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class StaffUserController {
	private final UserRepository users;
	private final RoleRepository roles;
	private final UserRoleRepository userRoles;
	private final UserOutletRepository userOutlets;
	private final PasswordEncoder encoder;

	public StaffUserController(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
			UserOutletRepository userOutlets, PasswordEncoder encoder) {
		this.users = users;
		this.roles = roles;
		this.userRoles = userRoles;
		this.userOutlets = userOutlets;
		this.encoder = encoder;
	}

	@PostMapping
	@Transactional
	public Map<String, Object> create(@RequestBody Map<String, String> body) {
		var p = TenantContext.require();
		if (!p.hasRole("OWNER") && !p.hasRole("MANAGER")) {
			throw ApiException.forbidden("RBAC", "Cannot create users");
		}
		UserEntity u = new UserEntity();
		u.setTenantId(p.tenantId());
		u.setEmail(body.get("email").toLowerCase());
		u.setPasswordHash(encoder.encode(body.get("password")));
		u.setName(body.getOrDefault("name", "Staff"));
		users.save(u);
		String roleCode = body.getOrDefault("role", "CASHIER");
		RoleEntity role = roles.findByTenantId(p.tenantId()).stream()
				.filter(r -> r.getCode().equals(roleCode)).findFirst()
				.orElseThrow(() -> ApiException.bad("ROLE", "Unknown role"));
		UserRoleEntity ur = new UserRoleEntity();
		ur.setTenantId(p.tenantId());
		ur.setUserId(u.getId());
		ur.setRoleId(role.getId());
		userRoles.save(ur);
		UUID outletId = body.get("outletId") == null ? p.outletId() : UUID.fromString(body.get("outletId"));
		if (outletId != null) {
			UserOutletEntity uo = new UserOutletEntity();
			uo.setTenantId(p.tenantId());
			uo.setUserId(u.getId());
			uo.setOutletId(outletId);
			userOutlets.save(uo);
		}
		return Map.of("id", u.getId(), "email", u.getEmail(), "role", roleCode);
	}
}
