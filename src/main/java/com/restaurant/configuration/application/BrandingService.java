package com.restaurant.configuration.application;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.restaurant.configuration.api.BrandingResponse;
import com.restaurant.configuration.infrastructure.*;
import com.restaurant.outlet.infrastructure.OutletRepository;
import com.restaurant.platform.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class BrandingService {
    private static final String KEY = "branding";
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> FONTS = Set.of("Inter", "Plus Jakarta Sans", "Roboto", "Poppins", "Lato",
            "Montserrat", "Open Sans", "Nunito", "Merriweather");
    private static final Set<String> FIELDS = Set.of("businessName", "workspaceLabel", "logoUrl", "compactLogoUrl",
            "faviconUrl", "logoBase64", "compactLogoBase64", "faviconBase64", "primaryColor", "secondaryColor",
            "backgroundColor", "surfaceColor", "textColor", "headingFont", "bodyFont", "borderRadius", "receiptHeader",
            "receiptFooter");
    private static final Map<String, Object> DEFAULTS = Map.ofEntries(Map.entry("businessName", "Kadai"),
            Map.entry("workspaceLabel", "Restaurant workspace"), Map.entry("logoUrl", ""),
            Map.entry("compactLogoUrl", ""), Map.entry("faviconUrl", ""), Map.entry("logoBase64", ""),
            Map.entry("compactLogoBase64", ""), Map.entry("faviconBase64", ""), Map.entry("primaryColor", "#2f6f62"),
            Map.entry("secondaryColor", "#d06b45"), Map.entry("backgroundColor", "#f6f7f3"),
            Map.entry("surfaceColor", "#ffffff"), Map.entry("textColor", "#20312d"),
            Map.entry("headingFont", "Plus Jakarta Sans"), Map.entry("bodyFont", "Inter"),
            Map.entry("borderRadius", 14), Map.entry("receiptHeader", "Kadai"),
            Map.entry("receiptFooter", "Thank you. Please visit again."));
    private final ConfigEntryRepository entries;
    private final OutletRepository outlets;
    private final JsonMapper json;
    private final AuditWriter audit;

    public BrandingService(ConfigEntryRepository e, OutletRepository o, JsonMapper j, AuditWriter a) {
        entries = e;
        outlets = o;
        json = j;
        audit = a;
    }

    @Transactional(readOnly = true)
    public BrandingResponse effective(UUID outletId) {
        var p = TenantContext.require();
        if (p.isGuest())
            throw ApiException.forbidden("RBAC", "Staff access required");
        if (outletId != null)
            access(outletId, false);
        var t = tenant(p.tenantId()).orElse(null);
        var o = outletId == null ? null : outlet(p.tenantId(), outletId).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>(DEFAULTS);
        if (t != null)
            out.putAll(read(t));
        if (o != null)
            out.putAll(read(o));
        long tv = t == null ? 0 : t.getVersion();
        Long ov = o == null ? null : o.getVersion();
        return new BrandingResponse(Collections.unmodifiableMap(out), tv, ov, "t" + tv + "-o" + (ov == null ? 0 : ov),
                latest(t, o));
    }

    @Transactional
    public BrandingResponse updateTenant(Map<String, Object> patch, long expected) {
        var p = TenantContext.require();
        if (!p.hasRole("OWNER"))
            throw ApiException.forbidden("RBAC", "Only an owner can change tenant branding");
        var clean = validate(patch);
        var e = tenant(p.tenantId()).orElseGet(() -> fresh("TENANT", null));
        check(e, expected);
        var value = read(e);
        value.putAll(clean);
        write(e, value, p.userId());
        entries.saveAndFlush(e);
        audit.write("BRANDING_UPDATED", "CONFIGURATION", e.getId(),
                "scope=TENANT fields=" + new TreeSet<>(clean.keySet()) + " version=" + e.getVersion());
        return effective(null);
    }

    @Transactional
    public BrandingResponse updateOutlet(UUID id, Map<String, Object> patch, long expected) {
        var p = TenantContext.require();
        access(id, true);
        var clean = validate(patch);
        var e = outlet(p.tenantId(), id).orElseGet(() -> fresh("OUTLET", id));
        check(e, expected);
        var value = read(e);
        value.putAll(clean);
        write(e, value, p.userId());
        entries.saveAndFlush(e);
        audit.write("BRANDING_UPDATED", "CONFIGURATION", e.getId(), "scope=OUTLET outlet=" + id + " fields="
                + new TreeSet<>(clean.keySet()) + " version=" + e.getVersion());
        return effective(id);
    }

    @Transactional
    public BrandingResponse clearOutlet(UUID id, long expected) {
        var p = TenantContext.require();
        access(id, true);
        var e = outlet(p.tenantId(), id)
                .orElseThrow(() -> ApiException.notFound("BRANDING_OVERRIDE", "No outlet branding override exists"));
        check(e, expected);
        entries.delete(e);
        audit.write("BRANDING_RESET", "CONFIGURATION", e.getId(), "scope=OUTLET outlet=" + id);
        return effective(id);
    }

    private void access(UUID id, boolean write) {
        var p = TenantContext.require();
        var o = outlets.findById(id).orElseThrow(() -> ApiException.notFound("OUTLET", "Outlet not found"));
        if (!o.getTenantId().equals(p.tenantId()))
            throw ApiException.notFound("OUTLET", "Outlet not found");
        if (write && !(p.hasRole("OWNER") || p.hasRole("MANAGER")))
            throw ApiException.forbidden("RBAC", "Cannot change outlet branding");
        if (!p.hasRole("OWNER") && p.outletIds() != null && !p.outletIds().isEmpty() && !p.outletIds().contains(id))
            throw ApiException.forbidden("OUTLET_SCOPE", "Outlet is outside your assigned scope");
    }

    private Map<String, Object> validate(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty())
            throw ApiException.bad("BRANDING_EMPTY", "At least one branding field is required");
        Map<String, Object> clean = new LinkedHashMap<>();
        for (var x : patch.entrySet()) {
            String k = x.getKey();
            Object raw = x.getValue();
            if (!FIELDS.contains(k))
                throw ApiException.bad("BRANDING_FIELD", "Unknown branding field: " + k);
            if (raw == null)
                throw ApiException.bad("BRANDING_VALUE", k + " cannot be null");
            if (k.endsWith("Color")) {
                String v = text(raw, k, 7, false);
                if (!v.startsWith("#"))
                    v = "#" + v;
                if (!COLOR.matcher(v).matches())
                    throw ApiException.bad("BRANDING_COLOR", k + " must be a six-digit hex color such as FF7200");
                clean.put(k, v.toLowerCase(Locale.ROOT));
            } else if (k.endsWith("Base64")) {
                clean.put(k, image(raw, k));
            } else if (k.endsWith("Url")) {
                String v = text(raw, k, 2048, true);
                url(v, k);
                clean.put(k, v);
            } else if (k.endsWith("Font")) {
                String v = text(raw, k, 64, false);
                if (!FONTS.contains(v))
                    throw ApiException.bad("BRANDING_FONT", "Unsupported font: " + v);
                clean.put(k, v);
            } else if (k.equals("borderRadius")) {
                if (!(raw instanceof Number n) || n.intValue() < 0 || n.intValue() > 28)
                    throw ApiException.bad("BRANDING_RADIUS", "borderRadius must be between 0 and 28");
                clean.put(k, n.intValue());
            } else {
                int max = k.equals("receiptFooter") ? 500 : k.equals("receiptHeader") ? 120 : 100;
                clean.put(k, text(raw, k, max, false));
            }
        }
        try {
            if (json.writeValueAsBytes(clean).length > 4_500_000)
                throw ApiException.bad("BRANDING_SIZE", "Combined branding payload is too large");
        } catch (JacksonException e) {
            throw ApiException.bad("BRANDING_JSON", "Branding payload is invalid");
        }
        return clean;
    }

    private static String text(Object raw, String key, int max, boolean empty) {
        if (!(raw instanceof String s))
            throw ApiException.bad("BRANDING_TYPE", key + " must be text");
        s = s.strip();
        if (!empty && s.isEmpty())
            throw ApiException.bad("BRANDING_VALUE", key + " cannot be empty");
        if (s.length() > max)
            throw ApiException.bad("BRANDING_LENGTH", key + " is too long");
        if (s.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            throw ApiException.bad("BRANDING_VALUE", key + " contains invalid characters");
        return s;
    }

    private static void url(String v, String k) {
        if (v.isEmpty())
            return;
        try {
            URI u = URI.create(v);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null)
                throw ApiException.bad("BRANDING_URL", k + " must be an HTTPS URL without embedded credentials");
        } catch (IllegalArgumentException e) {
            throw ApiException.bad("BRANDING_URL", k + " is not a valid URL");
        }
    }

    private static String image(Object raw, String key) {
        String value = text(raw, key, 2_800_000, true);
        if (value.isEmpty())
            return "";
        String declared = null, payload = value;
        if (value.startsWith("data:")) {
            int comma = value.indexOf(',');
            if (comma < 0)
                throw ApiException.bad("BRANDING_IMAGE", "Invalid image data URL");
            String meta = value.substring(5, comma).toLowerCase(Locale.ROOT);
            if (!meta.endsWith(";base64"))
                throw ApiException.bad("BRANDING_IMAGE", "Image data must use Base64 encoding");
            declared = meta.substring(0, meta.length() - 7);
            payload = value.substring(comma + 1);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw ApiException.bad("BRANDING_IMAGE_BASE64", "Image contains invalid Base64");
        }
        int max = key.equals("logoBase64") ? 2 * 1024 * 1024
                : key.equals("compactLogoBase64") ? 1024 * 1024 : 256 * 1024;
        if (bytes.length == 0 || bytes.length > max)
            throw ApiException.bad("BRANDING_IMAGE_SIZE", key + " exceeds its decoded size limit");
        String detected = type(bytes);
        if (detected == null)
            throw ApiException.bad("BRANDING_IMAGE_TYPE", "Only PNG, JPEG, and WebP images are accepted");
        if (declared != null && !declared.equals(detected))
            throw ApiException.bad("BRANDING_IMAGE_MIME", "Declared image type does not match file content");
        return "data:" + detected + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String type(byte[] b) {
        if (b.length >= 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71 && b[4] == 13 && b[5] == 10
                && b[6] == 26 && b[7] == 10)
            return "image/png";
        if (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255)
            return "image/jpeg";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
                && b[10] == 'B' && b[11] == 'P')
            return "image/webp";
        return null;
    }

    private Optional<ConfigEntryEntity> tenant(UUID t) {
        return entries.findByTenantIdAndScopeAndScopeIdIsNullAndKey(t, "TENANT", KEY);
    }

    private Optional<ConfigEntryEntity> outlet(UUID t, UUID o) {
        return entries.findByTenantIdAndScopeAndScopeIdAndKey(t, "OUTLET", o, KEY);
    }

    private ConfigEntryEntity fresh(String scope, UUID id) {
        var p = TenantContext.require();
        var e = new ConfigEntryEntity();
        e.setTenantId(p.tenantId());
        e.setScope(scope);
        e.setScopeId(id);
        e.setKey(KEY);
        e.setValue("{}");
        return e;
    }

    private Map<String, Object> read(ConfigEntryEntity e) {
        if (e == null || e.getValue() == null || e.getValue().isBlank())
            return new LinkedHashMap<>();
        try {
            return new LinkedHashMap<>(json.readValue(e.getValue(), new TypeReference<Map<String, Object>>() {
            }));
        } catch (JacksonException x) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BRANDING_STORED_JSON",
                    "Stored branding configuration is invalid");
        }
    }

    private void write(ConfigEntryEntity e, Map<String, Object> v, UUID actor) {
        try {
            e.setValue(json.writeValueAsString(v));
            e.setUpdatedAt(Instant.now());
            e.setUpdatedBy(actor);
        } catch (JacksonException x) {
            throw ApiException.bad("BRANDING_JSON", "Branding payload is invalid");
        }
    }

    private static void check(ConfigEntryEntity e, long expected) {
        if (expected < 0)
            throw ApiException.bad("VERSION", "Version cannot be negative");
        if (e.getVersion() != expected)
            throw ApiException.conflict("VERSION_CONFLICT", "Branding changed on another device; reload before saving");
    }

    private static Instant latest(ConfigEntryEntity a, ConfigEntryEntity b) {
        if (a == null)
            return b == null ? null : b.getUpdatedAt();
        if (b == null)
            return a.getUpdatedAt();
        return a.getUpdatedAt().isAfter(b.getUpdatedAt()) ? a.getUpdatedAt() : b.getUpdatedAt();
    }
}
