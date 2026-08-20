package com.restaurant.identity.application;

import com.restaurant.identity.infrastructure.PasswordResetTokenEntity;
import com.restaurant.identity.infrastructure.PasswordResetTokenRepository;
import com.restaurant.identity.infrastructure.RefreshTokenEntity;
import com.restaurant.identity.infrastructure.RefreshTokenRepository;
import com.restaurant.identity.infrastructure.RoleEntity;
import com.restaurant.identity.infrastructure.RoleRepository;
import com.restaurant.identity.infrastructure.UserEntity;
import com.restaurant.identity.infrastructure.UserOutletEntity;
import com.restaurant.identity.infrastructure.UserOutletRepository;
import com.restaurant.identity.infrastructure.UserRepository;
import com.restaurant.identity.infrastructure.UserRoleEntity;
import com.restaurant.identity.infrastructure.UserRoleRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.IdempotencyService;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {
	private static final Logger log = LoggerFactory.getLogger(AuthService.class);
	private final UserRepository users;
	private final RoleRepository roles;
	private final UserRoleRepository userRoles;
	private final UserOutletRepository userOutlets;
	private final RefreshTokenRepository refreshTokens;
	private final PasswordResetTokenRepository resets;
	private final PasswordEncoder encoder;
	private final JwtService jwt;
	private final StringRedisTemplate redis;
	private final com.restaurant.platform.api.AppProperties props;
	private final AccessManagementService access;

	public AuthService(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
			UserOutletRepository userOutlets, RefreshTokenRepository refreshTokens,
			PasswordResetTokenRepository resets, PasswordEncoder encoder, JwtService jwt,
			StringRedisTemplate redis, com.restaurant.platform.api.AppProperties props, AccessManagementService access) {
		this.users = users;
		this.roles = roles;
		this.userRoles = userRoles;
		this.userOutlets = userOutlets;
		this.refreshTokens = refreshTokens;
		this.resets = resets;
		this.encoder = encoder;
		this.jwt = jwt;
		this.redis = redis;
		this.props = props;
		this.access = access;
	}

	@Transactional
	public Map<String, Object> login(String email, String password, String ip) {
		String rk = "rl:login:" + ip + ":" + email.toLowerCase();
		Long n = redis.opsForValue().increment(rk);
		if (n != null && n == 1L) redis.expire(rk, Duration.ofMinutes(1));
		if (n != null && n > 10) throw ApiException.bad("RATE_LIMIT", "Too many login attempts");

		List<UserEntity> found = users.lookupByEmail(email);
		if (found.isEmpty() || !encoder.matches(password, found.getFirst().getPasswordHash())) {
			throw ApiException.unauthorized("Bad credentials");
		}
		UserEntity u = found.getFirst();
		if (!"ACTIVE".equals(u.getStatus())) throw ApiException.unauthorized("Account is not active");
		TenantContext.set(new TenantPrincipal(u.getTenantId(), u.getId(), List.of(), Set.of(), "staff", null, null, null, null));
		return issue(u);
	}

	@Transactional
	public Map<String, Object> refresh(String refreshRaw) {
		String hash = IdempotencyService.sha256(refreshRaw);
		var found = refreshTokens.lookupByHash(hash);
		if (found.isEmpty()) throw ApiException.unauthorized("Invalid refresh");
		RefreshTokenEntity rt = found.getFirst();
		TenantContext.set(new TenantPrincipal(rt.getTenantId(), rt.getUserId(), List.of(), Set.of(), "staff", null, null, null, null));
		rt = refreshTokens.findById(rt.getId()).orElse(rt);
		if (rt.isRevoked() || rt.getExpiresAt().isBefore(Instant.now())) {
			rt.setRevoked(true);
			refreshTokens.save(rt);
			throw ApiException.unauthorized("Refresh reuse revoked");
		}
		rt.setRevoked(true);
		refreshTokens.save(rt);
		UserEntity u = users.findById(rt.getUserId()).orElseThrow(() -> ApiException.unauthorized("User gone"));
		if (!"ACTIVE".equals(u.getStatus())) throw ApiException.unauthorized("Account is not active");
		TenantContext.set(new TenantPrincipal(u.getTenantId(), u.getId(), List.of(), Set.of(), "staff", null, null, null, null));
		return issue(u);
	}

	@Transactional
	public void logout(String refreshRaw) {
		refreshTokens.findByTokenHash(IdempotencyService.sha256(refreshRaw)).ifPresent(rt -> {
			rt.setRevoked(true);
			refreshTokens.save(rt);
		});
	}

	@Transactional
	public Map<String, String> requestReset(String email) {
		List<UserEntity> found = users.lookupByEmail(email);
		if (found.isEmpty()) return Map.of("status", "ok");
		UserEntity u = found.getFirst();
		TenantContext.set(new TenantPrincipal(u.getTenantId(), u.getId(), List.of(), Set.of(), "staff", null, null, null, null));
		String token = jwt.refreshToken();
		PasswordResetTokenEntity e = new PasswordResetTokenEntity();
		e.setTenantId(u.getTenantId());
		e.setUserId(u.getId());
		e.setTokenHash(IdempotencyService.sha256(token));
		e.setExpiresAt(Instant.now().plus(Duration.ofHours(2)));
		resets.save(e);
		log.info("password-reset token (non-prod) user={} token={}", u.getEmail(), token);
		return Map.of("status", "ok", "devToken", token);
	}

	@Transactional
	public void confirmReset(String token, String newPassword) {
		PasswordResetTokenEntity e = resets.findByTokenHash(IdempotencyService.sha256(token))
				.orElseThrow(() -> ApiException.bad("RESET_INVALID", "Invalid token"));
		if (e.isUsed() || e.getExpiresAt().isBefore(Instant.now())) {
			throw ApiException.bad("RESET_INVALID", "Invalid token");
		}
		UserEntity u = users.findById(e.getUserId()).orElseThrow();
		TenantContext.set(new TenantPrincipal(u.getTenantId(), u.getId(), List.of(), Set.of(), "staff", null, null, null, null));
		u.setPasswordHash(encoder.encode(newPassword));
		users.save(u);
		e.setUsed(true);
		resets.save(e);
	}

	private Map<String, Object> issue(UserEntity u) {
		List<UserRoleEntity> urs = userRoles.findByUserId(u.getId());
		Set<UUID> roleIds = urs.stream().map(UserRoleEntity::getRoleId).collect(Collectors.toSet());
		Set<String> roleCodes = new HashSet<>();
		for (RoleEntity r : roles.findByTenantId(u.getTenantId())) {
			if (roleIds.contains(r.getId())) roleCodes.add(r.getCode());
		}
		List<UUID> outletIds = userOutlets.findByUserId(u.getId()).stream().map(UserOutletEntity::getOutletId).toList();
		Set<String> permissionCodes = access.permissionsFor(u.getTenantId(), u.getId());
		String access = jwt.staffToken(u.getTenantId(), u.getId(), outletIds, roleCodes);
		String refresh = jwt.refreshToken();
		RefreshTokenEntity rt = new RefreshTokenEntity();
		rt.setTenantId(u.getTenantId());
		rt.setUserId(u.getId());
		rt.setTokenHash(IdempotencyService.sha256(refresh));
		rt.setExpiresAt(Instant.now().plusSeconds(props.getJwt().getRefreshTtlSeconds()));
		refreshTokens.save(rt);
		return Map.of(
				"accessToken", access,
				"refreshToken", refresh,
				"tenantId", u.getTenantId().toString(),
				"userId", u.getId().toString(),
				"roles", roleCodes,
				"permissions", permissionCodes,
				"outletIds", outletIds.stream().map(UUID::toString).toList()
		);
	}
}
