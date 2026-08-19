package com.restaurant.platform.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = request.getHeader("X-Request-Id");
		if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
		request.setAttribute("requestId", id);
		MDC.put("request_id", id);
		response.setHeader("X-Request-Id", id);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.clear();
			com.restaurant.platform.api.TenantContext.clear();
		}
	}
}
