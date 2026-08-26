package com.restaurant.platform.api;

import com.restaurant.platform.application.PlatformAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/administrators")
public class PlatformAdministratorController {
    private final PlatformAuthService service;
    public PlatformAdministratorController(PlatformAuthService service) { this.service = service; }

    @GetMapping public List<Map<String,Object>> list() { return service.administrators(); }

    @PostMapping public Map<String,Object> invite(@RequestBody Map<String,String> body) {
        return service.invite(principal().userId(), body.get("email"), body.get("displayName"));
    }

    @PostMapping("/{id}/status") public ResponseEntity<Void> status(@PathVariable UUID id, @RequestBody Map<String,String> body) {
        service.changeStatus(principal().userId(), id, body.get("status"));
        return ResponseEntity.noContent().build();
    }

    private TenantPrincipal principal() {
        TenantPrincipal principal = TenantContext.get();
        if (principal == null || !"platform".equals(principal.typ()) || principal.userId() == null) throw ApiException.unauthorized("Platform administrator required");
        return principal;
    }
}
