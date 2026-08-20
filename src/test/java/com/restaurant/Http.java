package com.restaurant;

import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

public class Http {
	private final RestTemplate http = new RestTemplate();
	private final String base;
	private String bearer;

	public Http(String base) { this.base = base; }

	public Http auth(String token) { this.bearer = token; return this; }

	public Map<String, Object> post(String path, String json) {
		return post(path, json, null);
	}

	public Map<String, Object> post(String path, String json, String idem) {
		try {
			ResponseEntity<String> res = http.exchange(base + path, HttpMethod.POST, entity(json, idem), String.class);
			return parse(res.getBody());
		} catch (HttpStatusCodeException e) {
			throw new AssertionError(e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
		}
	}

	public ResponseEntity<String> postRaw(String path, String json, String idem) {
		try {
			return http.exchange(base + path, HttpMethod.POST, entity(json, idem), String.class);
		} catch (HttpStatusCodeException e) {
			return ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsString());
		}
	}

	public Map<String, Object> get(String path) {
		try {
			ResponseEntity<String> res = http.exchange(base + path, HttpMethod.GET, entity(null, null), String.class);
			return parse(res.getBody());
		} catch (HttpStatusCodeException e) {
			throw new AssertionError(e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
		}
	}

	public ResponseEntity<String> getRaw(String path) {
		try {
			return http.exchange(base + path, HttpMethod.GET, entity(null, null), String.class);
		} catch (HttpStatusCodeException e) {
			return ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsString());
		}
	}

	public ResponseEntity<String> patchRaw(String path, String json) {
		try {
			return http.exchange(base + path, HttpMethod.PATCH, entity(json, null), String.class);
		} catch (HttpStatusCodeException e) {
			return ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsString());
		}
	}

	public ResponseEntity<String> putRaw(String path, String json, long version) {
		try {
			HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); h.setBearerAuth(bearer); h.set("If-Match", String.valueOf(version));
			return http.exchange(base + path, HttpMethod.PUT, new HttpEntity<>(json, h), String.class);
		} catch (HttpStatusCodeException e) {
			return ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsString());
		}
	}

	private HttpEntity<String> entity(String json, String idem) {
		HttpHeaders h = new HttpHeaders();
		h.setContentType(MediaType.APPLICATION_JSON);
		if (bearer != null) h.setBearerAuth(bearer);
		if (idem != null) h.add("Idempotency-Key", idem);
		return new HttpEntity<>(json, h);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parse(String body) {
		if (body == null || body.isBlank()) return Map.of();
		if (body.trim().startsWith("[")) return Map.of("list", new JacksonJsonParser().parseList(body));
		return new JacksonJsonParser().parseMap(body);
	}

	public static String uuid(Map<String, Object> m, String k) {
		return String.valueOf(m.get(k));
	}
}
