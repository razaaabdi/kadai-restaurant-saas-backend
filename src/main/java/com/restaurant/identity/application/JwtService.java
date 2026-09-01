package com.restaurant.identity.application;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.AppProperties;
import com.restaurant.platform.api.TenantPrincipal;
import com.restaurant.platform.api.PlatformTokenService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtService implements PlatformTokenService {
	private final AppProperties props;

	public JwtService(AppProperties props) {
		this.props = props;
	}

	private byte[] key() {
		byte[] raw = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
		if (raw.length >= 32) return Arrays.copyOf(raw, 32);
		byte[] k = new byte[32];
		System.arraycopy(raw, 0, k, 0, raw.length);
		return k;
	}

	public String staffToken(UUID tenantId, UUID userId, List<UUID> outletIds, Set<String> roles) {
		return sign(new JWTClaimsSet.Builder()
				.subject(userId.toString())
				.claim("typ", "staff")
				.claim("tenant_id", tenantId.toString())
				.claim("outlet_ids", outletIds.stream().map(UUID::toString).toList())
				.claim("roles", List.copyOf(roles))
				.issueTime(new Date())
				.expirationTime(Date.from(Instant.now().plusSeconds(props.getJwt().getStaffTtlSeconds())))
				.build());
	}

	public String guestToken(UUID tenantId, UUID outletId, UUID tableId, UUID sessionId, UUID qrTokenId) {
		return sign(new JWTClaimsSet.Builder()
				.subject("guest:" + sessionId)
				.claim("typ", "table_guest")
				.claim("tenant_id", tenantId.toString())
				.claim("outlet_id", outletId.toString())
				.claim("table_id", tableId.toString())
				.claim("session_id", sessionId.toString())
				.claim("qr_token_id", qrTokenId.toString())
				.issueTime(new Date())
				.expirationTime(Date.from(Instant.now().plusSeconds(props.getJwt().getGuestTtlSeconds())))
				.build());
	}

	public String platformToken(UUID administratorId) {
		return sign(new JWTClaimsSet.Builder()
				.subject(administratorId.toString())
				.claim("typ", "platform")
				.claim("roles", List.of("SUPER_ADMIN"))
				.issueTime(new Date())
				.expirationTime(Date.from(Instant.now().plusSeconds(props.getJwt().getStaffTtlSeconds())))
				.build());
	}

	public String refreshToken() {
		byte[] b = new byte[32];
		new java.security.SecureRandom().nextBytes(b);
		return HexFormat.of().formatHex(b);
	}

	public TenantPrincipal parse(String token) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!jwt.verify(new MACVerifier(key()))) {
				throw ApiException.unauthorized("Invalid token");
			}
			JWTClaimsSet c = jwt.getJWTClaimsSet();
			if (c.getExpirationTime() != null && c.getExpirationTime().toInstant().isBefore(Instant.now())) {
				throw ApiException.unauthorized("Expired token");
			}
			String typ = str(c, "typ");
			if ("platform".equals(typ)) {
				return new TenantPrincipal(null, UUID.fromString(c.getSubject()), List.of(), Set.of("SUPER_ADMIN"), typ,
						null, null, null, null);
			}
			UUID tenant = UUID.fromString(str(c, "tenant_id"));
			if ("table_guest".equals(typ)) {
				return new TenantPrincipal(tenant, null, List.of(), Set.of("GUEST"), typ,
						UUID.fromString(str(c, "table_id")),
						UUID.fromString(str(c, "session_id")),
						UUID.fromString(str(c, "qr_token_id")),
						UUID.fromString(str(c, "outlet_id")));
			}
			UUID user = UUID.fromString(c.getSubject());
			@SuppressWarnings("unchecked")
			List<String> outlets = (List<String>) c.getClaim("outlet_ids");
			@SuppressWarnings("unchecked")
			List<String> roles = (List<String>) c.getClaim("roles");
			List<UUID> outletIds = outlets == null ? List.of() : outlets.stream().map(UUID::fromString).toList();
			Set<String> roleSet = roles == null ? Set.of() : roles.stream().collect(Collectors.toSet());
			UUID primaryOutlet = outletIds.isEmpty() ? null : outletIds.getFirst();
			return new TenantPrincipal(tenant, user, outletIds, roleSet, typ, null, null, null, primaryOutlet);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			throw ApiException.unauthorized("Invalid token");
		}
	}

	private String sign(JWTClaimsSet claims) {
		try {
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			jwt.sign(new MACSigner(key()));
			return jwt.serialize();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static String str(JWTClaimsSet c, String n) throws java.text.ParseException {
		Object v = c.getClaim(n);
		return v == null ? null : v.toString();
	}
}
