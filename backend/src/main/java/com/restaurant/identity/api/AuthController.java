package com.restaurant.identity.api;

import com.restaurant.identity.application.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
	private final AuthService auth;

	public AuthController(AuthService auth) {
		this.auth = auth;
	}

	@PostMapping("/login")
	public Map<String, Object> login(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletRequest req) {
		return auth.login(body.get("email"), body.get("password"), req.getRemoteAddr());
	}

	@PostMapping("/refresh")
	public Map<String, Object> refresh(@RequestBody Map<String, String> body) {
		return auth.refresh(body.get("refreshToken"));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
		auth.logout(body.get("refreshToken"));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password-reset/request")
	public Map<String, String> requestReset(@RequestBody Map<String, String> body) {
		return auth.requestReset(body.get("email"));
	}

	@PostMapping("/password-reset/confirm")
	public ResponseEntity<Void> confirm(@RequestBody Map<String, String> body) {
		auth.confirmReset(body.get("token"), body.get("newPassword"));
		return ResponseEntity.noContent().build();
	}
}
