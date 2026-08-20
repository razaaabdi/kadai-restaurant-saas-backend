package com.restaurant.identity.api;

import com.restaurant.identity.application.AccessManagementService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class StaffUserController {
	private final AccessManagementService access;
	public StaffUserController(AccessManagementService access) { this.access = access; }

	@PostMapping
	@Transactional
	public Map<String, Object> create(@RequestBody Map<String, Object> body) { return access.create(body); }

	@GetMapping public java.util.List<Map<String,Object>> list() { return access.staff(); }
	@GetMapping("/access-catalog") public Map<String,Object> catalog() { return access.catalog(); }
	@GetMapping("/outlets") public java.util.List<Map<String,Object>> outlets() { return access.outlets(); }
	@PutMapping("/{userId}") public Map<String,Object> update(@PathVariable UUID userId, @RequestHeader("If-Match") long version, @RequestBody Map<String,Object> body) { return access.update(userId, body, version); }
}
