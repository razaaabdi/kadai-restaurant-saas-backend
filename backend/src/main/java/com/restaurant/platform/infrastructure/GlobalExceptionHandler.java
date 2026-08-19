package com.restaurant.platform.infrastructure;

import com.restaurant.platform.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Map<String, String>> handle(ApiException ex, HttpServletRequest req) {
		return body(ex.status(), ex.code(), ex.getMessage(), req);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<Map<String, String>> lock(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
		return body(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Stale version", req);
	}

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> handleOther(Exception ex, HttpServletRequest req) {
		log.error("unhandled", ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Unexpected error", req);
	}

	private ResponseEntity<Map<String, String>> body(HttpStatus status, String code, String message, HttpServletRequest req) {
		String rid = (String) req.getAttribute("requestId");
		if (rid == null) rid = UUID.randomUUID().toString();
		return ResponseEntity.status(status).body(Map.of("code", code, "message", message, "requestId", rid));
	}
}
