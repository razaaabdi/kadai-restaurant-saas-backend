package com.restaurant.outlet.application;

import com.restaurant.outlet.api.QrLookup;
import com.restaurant.outlet.infrastructure.AreaEntity;
import com.restaurant.outlet.infrastructure.AreaRepository;
import com.restaurant.outlet.infrastructure.DiningTableRepository;
import com.restaurant.outlet.infrastructure.OutletEntity;
import com.restaurant.outlet.infrastructure.OutletRepository;
import com.restaurant.outlet.infrastructure.QrTokenEntity;
import com.restaurant.outlet.infrastructure.QrTokenRepository;
import com.restaurant.outlet.infrastructure.TableEntity;
import com.restaurant.outlet.infrastructure.TableSessionEntity;
import com.restaurant.outlet.infrastructure.TableSessionRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.AppProperties;
import com.restaurant.platform.api.IdempotencyService;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FloorService {
	private final AreaRepository areas;
	private final DiningTableRepository tables;
	private final QrTokenRepository qrTokens;
	private final TableSessionRepository sessions;
	private final OutletRepository outlets;
	private final QrLookupDao lookup;
	private final AppProperties props;
	private final StringRedisTemplate redis;
	private final com.restaurant.identity.application.JwtService jwt;

	public FloorService(AreaRepository areas, DiningTableRepository tables, QrTokenRepository qrTokens,
			TableSessionRepository sessions, OutletRepository outlets, QrLookupDao lookup, AppProperties props,
			StringRedisTemplate redis, com.restaurant.identity.application.JwtService jwt) {
		this.areas = areas;
		this.tables = tables;
		this.qrTokens = qrTokens;
		this.sessions = sessions;
		this.outlets = outlets;
		this.lookup = lookup;
		this.props = props;
		this.redis = redis;
		this.jwt = jwt;
	}

	@Transactional
	public Map<String, Object> createArea(UUID outletId, String name) {
		requireStaff();
		AreaEntity a = new AreaEntity();
		a.setTenantId(TenantContext.require().tenantId());
		a.setOutletId(outletId);
		a.setName(name);
		areas.save(a);
		return Map.of("id", a.getId(), "name", name);
	}

	@Transactional
	public Map<String, Object> createTable(UUID areaId, String code, int seats) {
		requireStaff();
		AreaEntity area = areas.findById(areaId).orElseThrow(() -> ApiException.notFound("AREA", "Area not found"));
		TableEntity t = new TableEntity();
		t.setTenantId(TenantContext.require().tenantId());
		t.setOutletId(area.getOutletId());
		t.setAreaId(areaId);
		t.setCode(code);
		t.setSeats(seats);
		tables.save(t);
		return issueQr(t);
	}

	@Transactional
	public Map<String, Object> rotateQr(UUID tableId) {
		requireStaff();
		TableEntity t = tables.findById(tableId).orElseThrow(() -> ApiException.notFound("TABLE", "Table not found"));
		for (QrTokenEntity q : qrTokens.findByTableIdAndActiveTrue(tableId)) {
			q.setActive(false);
			qrTokens.save(q);
		}
		return issueQr(t);
	}

	@Transactional
	public void setQrLocked(UUID tableId, boolean locked) {
		requireStaff();
		TableEntity t = tables.findById(tableId).orElseThrow(() -> ApiException.notFound("TABLE", "Table not found"));
		t.setQrLocked(locked);
		tables.save(t);
	}

	@Transactional
	public void occupy(UUID tableId) {
		String status = tables.statusOf(tableId);
		if (status == null) throw ApiException.notFound("TABLE", "Table not found");
		if ("PAID_DIRTY".equals(status)) throw ApiException.conflict("TABLE_DIRTY", "Clear table first");
		if ("FREE".equals(status)) tables.updateStatus(tableId, "OCCUPIED");
	}

	@Transactional
	public void markBillRequested(UUID tableId) {
		tables.updateStatus(tableId, "BILL_REQUESTED");
	}

	@Transactional
	public void markPaidDirty(UUID tableId) {
		if (tableId == null) return;
		tables.updateStatus(tableId, "PAID_DIRTY");
	}

	@Transactional
	public void clearTable(UUID tableId) {
		requireStaff();
		if (tables.statusOf(tableId) == null) throw ApiException.notFound("TABLE", "Table not found");
		tables.updateStatus(tableId, "FREE");
	}

	public Map<String, Object> publicInfo(String token) {
		QrLookup q = requireToken(token);
		if (!q.active()) throw ApiException.gone("QR_ROTATED", "QR no longer valid");
		return Map.of(
				"outletName", q.outletName(),
				"tableCode", q.tableCode(),
				"qrOrderingEnabled", q.qrOrderingEnabled(),
				"tableStatus", q.tableStatus()
		);
	}

	@Transactional
	public Map<String, Object> openSession(String token, String ip) {
		String rk = "rl:qr:" + ip + ":" + token.substring(0, Math.min(8, token.length()));
		Long n = redis.opsForValue().increment(rk);
		if (n != null && n == 1L) redis.expire(rk, Duration.ofMinutes(1));
		if (n != null && n > 20) throw ApiException.bad("RATE_LIMIT", "Too many session attempts");

		QrLookup q = requireToken(token);
		if (!q.active()) throw ApiException.gone("QR_ROTATED", "QR no longer valid");
		if (!q.qrOrderingEnabled()) throw ApiException.conflict("QR_DISABLED", "QR ordering off");
		if (q.qrLocked()) throw ApiException.gone("QR_LOCKED", "Table QR locked");
		if ("PAID_DIRTY".equals(q.tableStatus())) throw ApiException.conflict("TABLE_DIRTY", "Table not cleared");

		TenantContext.set(new TenantPrincipal(q.tenantId(), null, List.of(), java.util.Set.of("GUEST"),
				"table_guest", q.tableId(), null, q.tokenId(), q.outletId()));
		TableSessionEntity s = new TableSessionEntity();
		s.setTenantId(q.tenantId());
		s.setOutletId(q.outletId());
		s.setTableId(q.tableId());
		s.setQrTokenId(q.tokenId());
		s.setExpiresAt(Instant.now().plusSeconds(props.getJwt().getGuestTtlSeconds()));
		sessions.save(s);
		String guestJwt = jwt.guestToken(q.tenantId(), q.outletId(), q.tableId(), s.getId(), q.tokenId());
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("accessToken", guestJwt);
		m.put("sessionId", s.getId());
		m.put("tableId", q.tableId());
		m.put("outletId", q.outletId());
		m.put("tenantId", q.tenantId());
		return m;
	}

	public QrLookup requireToken(String token) {
		QrLookup q = lookup.byPlainToken(token);
		if (q == null) throw ApiException.notFound("QR", "Unknown QR");
		return q;
	}

	public void assertGuestTokenLive(TenantPrincipal p) {
		if (p.qrTokenId() == null) throw ApiException.unauthorized("Not a guest");
		QrTokenEntity tok = qrTokens.findById(p.qrTokenId()).orElseThrow(() -> ApiException.gone("QR_ROTATED", "QR rotated"));
		if (!tok.isActive()) throw ApiException.gone("QR_ROTATED", "QR rotated");
	}

	public OutletEntity outlet(UUID id) {
		return outlets.findById(id).orElseThrow(() -> ApiException.notFound("OUTLET", "Outlet not found"));
	}

	public TableEntity table(UUID id) {
		return tables.findById(id).orElseThrow(() -> ApiException.notFound("TABLE", "Table not found"));
	}

	private Map<String, Object> issueQr(TableEntity t) {
		byte[] raw = new byte[24];
		new SecureRandom().nextBytes(raw);
		String token = HexFormat.of().formatHex(raw);
		QrTokenEntity q = new QrTokenEntity();
		q.setTenantId(t.getTenantId());
		q.setTableId(t.getId());
		q.setTokenHash(IdempotencyService.sha256(token));
		qrTokens.save(q);
		String payload = props.getPublicBase() + "/t/" + token;
		return Map.of("tableId", t.getId(), "token", token, "qrPayload", payload, "qrTokenId", q.getId());
	}

	private void requireStaff() {
		TenantPrincipal p = TenantContext.require();
		if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Guests cannot manage floor");
	}

	public List<AreaEntity> listAreas(UUID outletId) { return areas.findByOutletId(outletId); }
	public List<TableEntity> listTables(UUID outletId) { return tables.findByOutletId(outletId); }
}
