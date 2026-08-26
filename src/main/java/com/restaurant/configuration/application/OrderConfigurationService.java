package com.restaurant.configuration.application;

import com.restaurant.configuration.api.OrderConfigurationResponse;
import com.restaurant.configuration.infrastructure.ConfigEntryEntity;
import com.restaurant.configuration.infrastructure.ConfigEntryRepository;
import com.restaurant.outlet.infrastructure.OutletRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.AuditWriter;
import com.restaurant.platform.api.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderConfigurationService {
	private static final String KEY = "order_configuration";
	private static final Map<String, Object> DEFAULTS = Map.of(
			"dineInEnabled", true,
			"takeawayEnabled", true,
			"defaultDineInEntryMode", "ASK_EVERY_TIME",
			"allowAdditionalKot", true,
			"autoCompleteAfterFullPayment", true,
			"negativeStockPolicy", "WARN");
	private static final Set<String> ENTRY_MODES = Set.of("ASK_EVERY_TIME", "DIRECT_POS", "WAITER_PAPER_COUNTER");
	private static final Set<String> STOCK_POLICIES = Set.of("WARN", "BLOCK", "ALLOW");

	private final ConfigEntryRepository entries;
	private final OutletRepository outlets;
	private final JsonMapper json;
	private final AuditWriter audit;

	public OrderConfigurationService(ConfigEntryRepository entries, OutletRepository outlets, JsonMapper json,
			AuditWriter audit) {
		this.entries = entries;
		this.outlets = outlets;
		this.json = json;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public OrderConfigurationResponse effective(UUID outletId) {
		var principal = TenantContext.require();
		if (principal.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Staff access required");
		if (outletId != null) access(outletId, false);
		ConfigEntryEntity tenant = tenant(principal.tenantId()).orElse(null);
		ConfigEntryEntity outlet = outletId == null ? null : outlet(principal.tenantId(), outletId).orElse(null);
		Map<String, Object> result = new LinkedHashMap<>(DEFAULTS);
		result.putAll(read(tenant));
		result.putAll(read(outlet));
		return new OrderConfigurationResponse(Map.copyOf(result), tenant == null ? 0 : tenant.getVersion(),
				outlet == null ? null : outlet.getVersion());
	}

	@Transactional(readOnly = true)
	public OrderSettings settings(UUID outletId) {
		Map<String, Object> values = effective(outletId).configuration();
		return new OrderSettings(bool(values, "dineInEnabled"), bool(values, "takeawayEnabled"),
				String.valueOf(values.get("defaultDineInEntryMode")), bool(values, "allowAdditionalKot"),
				bool(values, "autoCompleteAfterFullPayment"), String.valueOf(values.get("negativeStockPolicy")));
	}

	@Transactional
	public OrderConfigurationResponse updateTenant(Map<String, Object> patch, long expectedVersion) {
		var principal = TenantContext.require();
		if (!principal.hasRole("OWNER")) throw ApiException.forbidden("ORDER_CONFIG", "Only an owner can change tenant order settings");
		ConfigEntryEntity entry = tenant(principal.tenantId()).orElseGet(() -> fresh("TENANT", null));
		update(entry, patch, expectedVersion);
		audit.write("ORDER_CONFIGURATION_UPDATED", "CONFIGURATION", entry.getId(), "scope=TENANT");
		return effective(null);
	}

	@Transactional
	public OrderConfigurationResponse updateOutlet(UUID outletId, Map<String, Object> patch, long expectedVersion) {
		access(outletId, true);
		var principal = TenantContext.require();
		ConfigEntryEntity entry = outlet(principal.tenantId(), outletId).orElseGet(() -> fresh("OUTLET", outletId));
		update(entry, patch, expectedVersion);
		audit.write("ORDER_CONFIGURATION_UPDATED", "CONFIGURATION", entry.getId(), "scope=OUTLET outlet=" + outletId);
		return effective(outletId);
	}

	private void update(ConfigEntryEntity entry, Map<String, Object> patch, long expectedVersion) {
		if (entry.getVersion() != expectedVersion) throw ApiException.conflict("VERSION_CONFLICT", "Order settings changed on another device; reload and try again");
		Map<String, Object> values = read(entry);
		values.putAll(validate(patch));
		try {
			entry.setValue(json.writeValueAsString(values));
			entry.setUpdatedAt(Instant.now());
			entry.setUpdatedBy(TenantContext.require().userId());
			entries.saveAndFlush(entry);
		} catch (JacksonException ex) {
			throw ApiException.bad("ORDER_CONFIG_JSON", "Order settings are invalid");
		}
	}

	private Map<String, Object> validate(Map<String, Object> patch) {
		if (patch == null || patch.isEmpty()) throw ApiException.bad("ORDER_CONFIG_EMPTY", "Choose at least one order setting");
		Map<String, Object> clean = new LinkedHashMap<>();
		for (var field : patch.entrySet()) {
			switch (field.getKey()) {
				case "dineInEnabled", "takeawayEnabled", "allowAdditionalKot", "autoCompleteAfterFullPayment" -> {
					if (!(field.getValue() instanceof Boolean value)) throw ApiException.bad("ORDER_CONFIG_TYPE", field.getKey() + " must be true or false");
					clean.put(field.getKey(), value);
				}
				case "defaultDineInEntryMode" -> clean.put(field.getKey(), enumValue(field.getValue(), ENTRY_MODES, field.getKey()));
				case "negativeStockPolicy" -> clean.put(field.getKey(), enumValue(field.getValue(), STOCK_POLICIES, field.getKey()));
				default -> throw ApiException.bad("ORDER_CONFIG_FIELD", "Unknown order setting: " + field.getKey());
			}
		}
		return clean;
	}

	private void access(UUID outletId, boolean write) {
		var principal = TenantContext.require();
		var found = outlets.findById(outletId).orElseThrow(() -> ApiException.notFound("OUTLET", "Outlet not found"));
		if (!found.getTenantId().equals(principal.tenantId())) throw ApiException.notFound("OUTLET", "Outlet not found");
		if (principal.outletIds() != null && !principal.outletIds().isEmpty() && !principal.outletIds().contains(outletId))
			throw ApiException.forbidden("OUTLET_SCOPE", "Outlet is outside your assigned scope");
		if (write && !(principal.hasRole("OWNER") || principal.hasRole("MANAGER")))
			throw ApiException.forbidden("ORDER_CONFIG", "Only an owner or manager can change outlet order settings");
	}

	private Optional<ConfigEntryEntity> tenant(UUID tenantId) {
		return entries.findByTenantIdAndScopeAndScopeIdIsNullAndKey(tenantId, "TENANT", KEY);
	}

	private Optional<ConfigEntryEntity> outlet(UUID tenantId, UUID outletId) {
		return entries.findByTenantIdAndScopeAndScopeIdAndKey(tenantId, "OUTLET", outletId, KEY);
	}

	private ConfigEntryEntity fresh(String scope, UUID scopeId) {
		ConfigEntryEntity entry = new ConfigEntryEntity();
		entry.setTenantId(TenantContext.require().tenantId());
		entry.setScope(scope);
		entry.setScopeId(scopeId);
		entry.setKey(KEY);
		entry.setValue("{}");
		return entry;
	}

	private Map<String, Object> read(ConfigEntryEntity entry) {
		if (entry == null || entry.getValue() == null || entry.getValue().isBlank()) return new LinkedHashMap<>();
		try {
			return new LinkedHashMap<>(json.readValue(entry.getValue(), new TypeReference<Map<String, Object>>() {}));
		} catch (JacksonException ex) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_CONFIG_STORED_JSON", "Stored order settings are invalid");
		}
	}

	private static boolean bool(Map<String, Object> values, String key) {
		return Boolean.TRUE.equals(values.get(key));
	}

	private static String enumValue(Object raw, Set<String> allowed, String field) {
		String value = raw == null ? "" : String.valueOf(raw).strip().toUpperCase();
		if (!allowed.contains(value)) throw ApiException.bad("ORDER_CONFIG_VALUE", "Unsupported value for " + field);
		return value;
	}
}
