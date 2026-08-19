package com.restaurant.platform.api;

import com.restaurant.platform.infrastructure.IdempotencyEntity;
import com.restaurant.platform.infrastructure.IdempotencyId;
import com.restaurant.platform.infrastructure.IdempotencyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
	private final IdempotencyRepository repo;
	public IdempotencyService(IdempotencyRepository repo) { this.repo = repo; }

	@Transactional
	public ResponseEntity<String> run(UUID tenantId, String key, String rawBody, Supplier<ResponseEntity<String>> action) {
		if (key == null || key.isBlank()) {
			throw ApiException.bad("IDEMPOTENCY_REQUIRED", "Idempotency-Key required");
		}
		String hash = sha256(rawBody == null ? "" : rawBody);
		var existing = repo.findById(new IdempotencyId(tenantId, key));
		if (existing.isPresent()) {
			var e = existing.get();
			if (!e.getRequestHash().equals(hash)) {
				throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "Same key, different body");
			}
			return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(e.getResponseBody());
		}
		ResponseEntity<String> res = action.get();
		IdempotencyEntity e = new IdempotencyEntity();
		e.setTenantId(tenantId);
		e.setKey(key);
		e.setRequestHash(hash);
		e.setStatusCode(res.getStatusCode().value());
		e.setResponseBody(res.getBody() == null ? "{}" : res.getBody());
		repo.save(e);
		return res;
	}

	public static String sha256(String s) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(d);
		} catch (Exception e) { throw new IllegalStateException(e); }
	}
}
