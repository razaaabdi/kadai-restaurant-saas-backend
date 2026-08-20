package com.restaurant.outlet.application;

import com.restaurant.outlet.api.QrLookup;
import com.restaurant.platform.api.IdempotencyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QrLookupDao {
	private final JdbcTemplate jdbc;

	public QrLookupDao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public QrLookup byPlainToken(String token) {
		String hash = IdempotencyService.sha256(token);
		List<QrLookup> rows = jdbc.query("SELECT * FROM lookup_qr_by_hash(?)", (rs, i) -> new QrLookup(
				rs.getObject("token_id", java.util.UUID.class),
				rs.getObject("tenant_id", java.util.UUID.class),
				rs.getObject("table_id", java.util.UUID.class),
				rs.getObject("outlet_id", java.util.UUID.class),
				rs.getBoolean("active"),
				rs.getString("table_status"),
				rs.getBoolean("qr_locked"),
				rs.getBoolean("qr_ordering_enabled"),
				rs.getString("outlet_name"),
				rs.getString("table_code")
		), hash);
		return rows.isEmpty() ? null : rows.getFirst();
	}
}
