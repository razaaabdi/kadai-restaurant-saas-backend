package com.restaurant.onboarding.api;

import com.restaurant.onboarding.application.OwnerAccountService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class OwnerAccountController {
    private final OwnerAccountService service;

    public OwnerAccountController(OwnerAccountService service) {
        this.service = service;
    }

    @GetMapping("/outlets")
    public List<Map<String, Object>> outlets() {
        return service.outlets();
    }

    @PostMapping("/outlets")
    public Map<String, Object> createOutlet(@RequestBody Map<String, Object> body) {
        return service.createOutlet(body);
    }

    @GetMapping("/subscription")
    public Map<String, Object> subscription() {
        return service.subscription();
    }

    @GetMapping("/features")
    public List<String> features() {
        return service.features();
    }
}
