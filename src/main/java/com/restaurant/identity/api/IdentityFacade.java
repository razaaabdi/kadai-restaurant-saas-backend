package com.restaurant.identity.api;

import com.restaurant.identity.infrastructure.RoleEntity;
import com.restaurant.identity.infrastructure.RoleRepository;
import com.restaurant.identity.infrastructure.UserEntity;
import com.restaurant.identity.infrastructure.UserOutletEntity;
import com.restaurant.identity.infrastructure.UserOutletRepository;
import com.restaurant.identity.infrastructure.UserRepository;
import com.restaurant.identity.infrastructure.UserRoleEntity;
import com.restaurant.identity.infrastructure.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IdentityFacade {
	private static final String[] ROLE_CODES = {"OWNER", "MANAGER", "SHIFT_MANAGER", "CASHIER", "WAITER", "KITCHEN", "INVENTORY_MANAGER"};
	private final UserRepository users;
	private final RoleRepository roles;
	private final UserRoleRepository userRoles;
	private final UserOutletRepository userOutlets;
	private final PasswordEncoder encoder;

	public IdentityFacade(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
			UserOutletRepository userOutlets, PasswordEncoder encoder) {
		this.users = users;
		this.roles = roles;
		this.userRoles = userRoles;
		this.userOutlets = userOutlets;
		this.encoder = encoder;
	}

	public UUID createOwner(UUID tenantId, UUID outletId, String email, String password, String name) {
		RoleEntity ownerRole = null;
		for (String code : ROLE_CODES) {
			RoleEntity r = new RoleEntity();
			r.setTenantId(tenantId);
			r.setCode(code);
			roles.save(r);
			if ("OWNER".equals(code)) ownerRole = r;
		}
		UserEntity u = new UserEntity();
		u.setTenantId(tenantId);
		u.setEmail(email.toLowerCase());
		u.setPasswordHash(encoder.encode(password));
		u.setName(name);
		users.save(u);
		UserRoleEntity ur = new UserRoleEntity();
		ur.setTenantId(tenantId);
		ur.setUserId(u.getId());
		ur.setRoleId(ownerRole.getId());
		userRoles.save(ur);
		UserOutletEntity uo = new UserOutletEntity();
		uo.setTenantId(tenantId);
		uo.setUserId(u.getId());
		uo.setOutletId(outletId);
		userOutlets.save(uo);
		return u.getId();
	}
}
