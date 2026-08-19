package com.restaurant.onboarding.api;

import com.restaurant.onboarding.application.OnboardingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {
	private final OnboardingService service;

	public OnboardingController(OnboardingService service) {
		this.service = service;
	}

	@PostMapping
	public Map<String, Object> onboard(@RequestBody Map<String, String> body) {
		return service.onboard(body.get("name"), body.get("slug"), body.get("email"), body.get("password"),
				body.getOrDefault("ownerName", "Owner"));
	}
}
