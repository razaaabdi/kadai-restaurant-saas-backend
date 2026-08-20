package com.restaurant.configuration.api;

import com.restaurant.configuration.application.BrandingService;
import com.restaurant.platform.api.ApiException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class BrandingController {
    private final BrandingService branding;

    public BrandingController(BrandingService b) {
        branding = b;
    }

    @GetMapping("/branding")
    public ResponseEntity<BrandingResponse> get(@RequestParam(required = false) UUID outletId) {
        var r = branding.effective(outletId);
        return ResponseEntity.ok().eTag(r.effectiveVersion()).cacheControl(CacheControl.noCache()).body(r);
    }

    @PutMapping("/branding")
    public ResponseEntity<BrandingResponse> tenant(
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String tag,
            @RequestBody Map<String, Object> body) {
        var r = branding.updateTenant(body, version(tag));
        return ResponseEntity.ok().eTag(r.effectiveVersion()).body(r);
    }

    @PutMapping("/outlets/{id}/branding")
    public ResponseEntity<BrandingResponse> outlet(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String tag,
            @RequestBody Map<String, Object> body) {
        var r = branding.updateOutlet(id, body, version(tag));
        return ResponseEntity.ok().eTag(r.effectiveVersion()).body(r);
    }

    @DeleteMapping("/outlets/{id}/branding")
    public ResponseEntity<BrandingResponse> clear(@PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String tag) {
        var r = branding.clearOutlet(id, version(tag));
        return ResponseEntity.ok().eTag(r.effectiveVersion()).body(r);
    }

    private static long version(String v) {
        if (v == null || v.isBlank())
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED",
                    "If-Match with the current numeric scope version is required");
        try {
            return Long.parseLong(v.strip().replace("\"", ""));
        } catch (NumberFormatException e) {
            throw ApiException.bad("IF_MATCH", "If-Match must contain the current numeric scope version");
        }
    }
}
