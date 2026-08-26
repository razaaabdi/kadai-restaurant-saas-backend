package com.restaurant.catalog.application;

import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class MenuImageService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final JdbcTemplate jdbc;

    public MenuImageService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Map<String, Object> upload(UUID outletId, MultipartFile file) {
        TenantPrincipal principal = TenantContext.require();
        if (!(principal.hasRole("OWNER") || principal.hasRole("MANAGER")))
            throw ApiException.forbidden("MENU_IMAGE_UPLOAD", "Only an owner or manager can upload menu images");
        if (principal.outletIds() == null || !principal.outletIds().contains(outletId))
            throw ApiException.forbidden("OUTLET_ACCESS", "You do not have access to this outlet");
        if (file == null || file.isEmpty()) throw ApiException.bad("IMAGE_REQUIRED", "Choose an image to upload");
        if (file.getSize() > MAX_BYTES) throw ApiException.bad("IMAGE_TOO_LARGE", "Image size must not exceed 5 MB");
        try {
            byte[] content = file.getBytes();
            String contentType = detectedType(content);
            UUID id = UUID.randomUUID();
            jdbc.update("insert into menu_item_images(id,tenant_id,outlet_id,content_type,content,size_bytes) values(?,?,?,?,?,?)",
                    id, principal.tenantId(), outletId, contentType, content, content.length);
            return Map.of("id", id, "url", "/api/v1/public/menu-images/" + id, "contentType", contentType, "sizeBytes", content.length);
        } catch (IOException ex) {
            throw ApiException.bad("IMAGE_READ_FAILED", "The selected image could not be read");
        }
    }

    public ImageData get(UUID id) {
        return jdbc.query("select content_type,content from menu_item_images where id=?", rs -> {
            if (!rs.next()) throw ApiException.notFound("MENU_IMAGE", "Menu image not found");
            return new ImageData(rs.getString("content_type"), rs.getBytes("content"));
        }, id);
    }

    private static String detectedType(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        throw ApiException.bad("IMAGE_TYPE", "Only PNG, JPEG, and WebP images are accepted");
    }

    public record ImageData(String contentType, byte[] content) {}
}
