package com.restaurant.identity.infrastructure;

import com.restaurant.identity.application.JwtService;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
	private final JwtService jwt;

	public JwtAuthFilter(JwtService jwt) {
		this.jwt = jwt;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String h = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (h != null && h.startsWith("Bearer ")) {
			try {
				TenantPrincipal p = jwt.parse(h.substring(7));
				TenantContext.set(p);
				MDC.put("tenant_id", p.tenantId() == null ? "" : p.tenantId().toString());
				MDC.put("user_id", p.userId() == null ? "" : p.userId().toString());
				MDC.put("outlet_id", p.outletId() == null ? "" : p.outletId().toString());
				var auths = (p.roles() == null ? java.util.Set.<String>of() : p.roles()).stream()
						.map(r -> new SimpleGrantedAuthority("ROLE_" + r))
						.collect(Collectors.toSet());
				SecurityContextHolder.getContext().setAuthentication(
						new UsernamePasswordAuthenticationToken(p, "n/a", auths));
			} catch (Exception ex) {
				response.setStatus(401);
				response.setContentType("application/json");
				response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid token\"}");
				return;
			}
		}
		chain.doFilter(request, response);
	}
}
