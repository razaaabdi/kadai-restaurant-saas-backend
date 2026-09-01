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
import com.restaurant.platform.api.AuditWriter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

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
	private final AuditWriter audit;

	public FloorService(AreaRepository areas, DiningTableRepository tables, QrTokenRepository qrTokens,
			TableSessionRepository sessions, OutletRepository outlets, QrLookupDao lookup, AppProperties props,
			StringRedisTemplate redis, com.restaurant.identity.application.JwtService jwt, AuditWriter audit) {
		this.areas = areas;
		this.tables = tables;
		this.qrTokens = qrTokens;
		this.sessions = sessions;
		this.outlets = outlets;
		this.lookup = lookup;
		this.props = props;
		this.redis = redis;
		this.audit = audit;
		this.jwt = jwt;
	}

	@Transactional
	public Map<String, Object> createArea(UUID outletId, String name) {
		requireFloorManager();
		requireOutletAccess(outletId);
		name = normalized(name, "Area name", 80);
		if (areas.existsByOutletIdAndNameIgnoreCase(outletId, name))
			throw ApiException.conflict("AREA_NAME_EXISTS", "An area with this name already exists");
		AreaEntity a = new AreaEntity();
		a.setTenantId(TenantContext.require().tenantId());
		a.setOutletId(outletId);
		a.setName(name);
		try { areas.saveAndFlush(a); } catch (DataIntegrityViolationException ex) {
			throw ApiException.conflict("AREA_NAME_EXISTS", "An area with this name already exists");
		}
		return Map.of("id", a.getId(), "name", name, "outletId", outletId, "tables", List.of());
	}

	@Transactional
	public Map<String, Object> createTable(UUID areaId, String code, int seats) {
		requireStaff();
		AreaEntity area = areas.findById(areaId).orElseThrow(() -> ApiException.notFound("AREA", "Area not found"));
		requireOutletAccess(area.getOutletId());
		code = normalized(code, "Table name", 40);
		validateSeats(seats);
		if (tables.existsByOutletIdAndCodeIgnoreCaseAndDeletedFalse(area.getOutletId(), code))
			throw ApiException.conflict("TABLE_CODE_EXISTS", "A table with this name already exists");
		TableEntity t = new TableEntity();
		t.setTenantId(TenantContext.require().tenantId());
		t.setOutletId(area.getOutletId());
		t.setAreaId(areaId);
		t.setCode(code);
		t.setSeats(seats);
		try { tables.saveAndFlush(t); } catch (DataIntegrityViolationException ex) {
			throw ApiException.conflict("TABLE_CODE_EXISTS", "A table with this name already exists");
		}
		return issueQr(t);
	}

	public List<Map<String, Object>> listFloor(UUID outletId) {
		requireStaff(); requireOutletAccess(outletId);
		return tables.findByOutletIdAndDeletedFalseOrderByCodeAsc(outletId).stream().map(this::tableView).toList();
	}

	public Map<String, Object> floorLayout(UUID outletId) {
		requireStaff(); requireOutletAccess(outletId);
		List<TableEntity> floorTables = tables.findByOutletIdAndDeletedFalseOrderByCodeAsc(outletId);
		List<Map<String, Object>> grouped = areas.findByOutletId(outletId).stream().map(area -> {
			Map<String, Object> view = new LinkedHashMap<>();
			view.put("id", area.getId()); view.put("name", area.getName()); view.put("outletId", area.getOutletId());
			view.put("tables", floorTables.stream().filter(table -> area.getId().equals(table.getAreaId())).map(this::tableView).toList());
			return view;
		}).toList();
		return Map.of("outletId", outletId, "areas", grouped);
	}

	@Transactional
	public Map<String, Object> updateTable(UUID tableId, String code, int seats, String status, Long expectedVersion) {
		requireStaff();
		TableEntity table = requireActiveTable(tableId); requireOutletAccess(table.getOutletId());
		if (expectedVersion != null && expectedVersion != table.getVersion())
			throw ApiException.conflict("STALE_TABLE", "The table was changed by another user; refresh and try again");
		code = normalized(code, "Table name", 40); validateSeats(seats); status = status == null ? table.getStatus() : status; validateManualStatus(status);
		if (!table.getCode().equalsIgnoreCase(code) && tables.existsByOutletIdAndCodeIgnoreCaseAndDeletedFalse(table.getOutletId(), code))
			throw ApiException.conflict("TABLE_CODE_EXISTS", "A table with this name already exists");
		if (!table.getStatus().equals(status) && (List.of("OCCUPIED", "BILL_REQUESTED").contains(table.getStatus()) || List.of("OCCUPIED", "BILL_REQUESTED").contains(status)))
			throw ApiException.conflict("ACTIVE_ORDER", "An occupied table status is controlled by its active order");
		table.setCode(code); table.setSeats(seats); table.setStatus(status);
		try { return tableView(tables.saveAndFlush(table)); } catch (DataIntegrityViolationException ex) {
			throw ApiException.conflict("TABLE_CODE_EXISTS", "A table with this name already exists");
		}
	}

	@Transactional
	public void removeTable(UUID tableId, Long expectedVersion) {
		requireStaff();
		TableEntity table = requireActiveTable(tableId); requireOutletAccess(table.getOutletId());
		if (expectedVersion != null && expectedVersion != table.getVersion())
			throw ApiException.conflict("STALE_TABLE", "The table was changed by another user; refresh and try again");
		if (!"FREE".equals(table.getStatus()))
			throw ApiException.conflict("TABLE_IN_USE", "Only an available table can be removed");
		for (QrTokenEntity token : qrTokens.findByTableIdAndActiveTrue(tableId)) { token.setActive(false); qrTokens.save(token); }
		table.setDeleted(true); tables.save(table);
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
	public void markPaymentPending(UUID tableId){
		if(tableId==null)return;String status=tables.statusOf(tableId);if(status==null)throw ApiException.notFound("TABLE","Table not found");
		if("BILL_REQUESTED".equals(status))tables.updateStatus(tableId,"OCCUPIED");
	}

	@Transactional
	public void markPaidDirty(UUID tableId) {
		if (tableId == null) return;
		tables.updateStatus(tableId, "CLEANING_REQUIRED");
	}

	@Transactional
	public void releaseAfterPayment(UUID tableId) { if(tableId!=null)tables.updateStatus(tableId,"FREE"); }

	@Transactional
	public void startCleaning(UUID tableId) {
		requireStaff(); TableEntity table = requireActiveTable(tableId); requireOutletAccess(table.getOutletId());
		if ("CLEANING".equals(table.getStatus())) return;
		if (!"CLEANING_REQUIRED".equals(table.getStatus())) throw ApiException.conflict("TABLE_TRANSITION", "Only a table awaiting cleaning can start cleaning");
		table.setStatus("CLEANING"); tables.save(table);
	}

	@Transactional
	public void completeCleaning(UUID tableId) {
		requireStaff(); TableEntity table = requireActiveTable(tableId); requireOutletAccess(table.getOutletId());
		if ("FREE".equals(table.getStatus())) return;
		if (!"CLEANING".equals(table.getStatus())) throw ApiException.conflict("TABLE_TRANSITION", "Cleaning must be started before it can be completed");
		table.setStatus("FREE"); tables.save(table);
	}

	@Transactional
	public void clearTable(UUID tableId) {
		requireStaff();
		if (tables.statusOf(tableId) == null) throw ApiException.notFound("TABLE", "Table not found");
		tables.updateStatus(tableId, "FREE");
	}

	@Transactional
	public Map<String,Object> reconcileOrphanedTables(UUID outletId) {
		requireFloorManager(); requireOutletAccess(outletId);
		TenantPrincipal p = TenantContext.require();
		int repaired = tables.reconcileOrphanedOccupied(p.tenantId(), outletId);
		if (repaired > 0) audit.write("ORPHANED_TABLES_RECONCILED", "OUTLET", outletId, "repaired=" + repaired);
		return Map.of("outletId", outletId, "repaired", repaired);
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
		return requireActiveTable(id);
	}

	@Transactional
	public TableEntity lockForOrder(UUID id) {
		TableEntity table = tables.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("TABLE", "Table not found"));
		if (table.isDeleted()) throw ApiException.notFound("TABLE", "Table not found");
		return table;
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

	private void requireFloorManager() {
		TenantPrincipal p = TenantContext.require();
		if (!(p.hasRole("OWNER") || p.hasRole("MANAGER")))
			throw ApiException.forbidden("FLOOR_AREA_MANAGE", "Only an owner or manager can create dining areas");
	}

	public List<AreaEntity> listAreas(UUID outletId) { requireStaff(); requireOutletAccess(outletId); return areas.findByOutletId(outletId); }
	public List<TableEntity> listTables(UUID outletId) { return tables.findByOutletIdAndDeletedFalseOrderByCodeAsc(outletId); }

	private TableEntity requireActiveTable(UUID id) {
		TableEntity table = tables.findById(id).orElseThrow(() -> ApiException.notFound("TABLE", "Table not found"));
		if (table.isDeleted()) throw ApiException.notFound("TABLE", "Table not found");
		return table;
	}

	private Map<String, Object> tableView(TableEntity table) {
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("tableId", table.getId()); view.put("outletId", table.getOutletId()); view.put("areaId", table.getAreaId());
		view.put("code", table.getCode()); view.put("seats", table.getSeats()); view.put("status", table.getStatus());
		view.put("qrLocked", table.isQrLocked()); view.put("version", table.getVersion());
		return view;
	}

	private void requireOutletAccess(UUID outletId) {
		TenantPrincipal p = TenantContext.require();
		if (p.outletIds() == null || !p.outletIds().contains(outletId))
			throw ApiException.forbidden("OUTLET_ACCESS", "You do not have access to this outlet");
		outlet(outletId);
	}

	private static String normalized(String value, String field, int max) {
		if (value == null || value.trim().isEmpty()) throw ApiException.bad("VALIDATION", field + " is required");
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.length() > max) throw ApiException.bad("VALIDATION", field + " must be " + max + " characters or fewer");
		return normalized;
	}
	private static void validateSeats(int seats) { if (seats < 1 || seats > 50) throw ApiException.bad("VALIDATION", "Seats must be between 1 and 50"); }
	private static void validateManualStatus(String status) { if (!List.of("FREE", "RESERVED", "PAID_DIRTY", "CLEANING_REQUIRED", "CLEANING", "OCCUPIED", "BILL_REQUESTED", "OUT_OF_SERVICE").contains(status)) throw ApiException.bad("VALIDATION", "Unknown table status"); }
}
