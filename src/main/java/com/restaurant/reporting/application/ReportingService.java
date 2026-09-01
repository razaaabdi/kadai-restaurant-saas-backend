package com.restaurant.reporting.application;

import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read models for the outlet owner.  All figures are scoped to the authenticated tenant and outlet. */
@Service
public class ReportingService {
    private final JdbcTemplate jdbc;

    public ReportingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> dashboard(UUID outletId, LocalDate date) {
        requireOutlet(outletId);
        Map<String, Object> sales = daily(outletId, date);
        long activeOrders = number("select count(*) from orders where tenant_id=? and outlet_id=? and status not in ('COMPLETED','CANCELLED','VOIDED')", outletId);
        long occupiedTables = number("select count(*) from tables where tenant_id=? and outlet_id=? and status='OCCUPIED'", outletId);
        long totalTables = number("select count(*) from tables where tenant_id=? and outlet_id=?", outletId);
        long lowStock = number("select count(*) from (select i.id from inventory_items i left join stock_balances b on b.inventory_item_id=i.id and b.tenant_id=i.tenant_id and b.outlet_id=i.outlet_id where i.tenant_id=? and i.outlet_id=? and i.active=true group by i.id,i.reorder_level having coalesce(sum(b.qty),0) <= i.reorder_level) low", outletId);
        Map<String, Object> result = new LinkedHashMap<>(sales);
        result.put("date", date.toString()); result.put("activeOrders", activeOrders);
        result.put("occupiedTables", occupiedTables); result.put("totalTables", totalTables);
        result.put("lowStockItems", lowStock); result.put("averageOrderPaise", ((Number) sales.get("ordersCount")).longValue() == 0 ? 0L : ((Number) sales.get("gmvPaise")).longValue() / ((Number) sales.get("ordersCount")).longValue());
        return result;
    }

    public Map<String, Object> salesReport(UUID outletId, LocalDate from, LocalDate to) {
        requireRange(from, to); requireOutlet(outletId);
        List<Map<String, Object>> days = jdbc.query("select business_date,orders_count,gmv_paise,cash_paise,upi_paise,card_paise from outlet_daily_sales where tenant_id=? and outlet_id=? and business_date between ? and ? order by business_date", (rs, n) -> Map.<String, Object>of("date", rs.getDate(1).toLocalDate().toString(), "ordersCount", rs.getLong(2), "gmvPaise", rs.getLong(3), "cashPaise", rs.getLong(4), "upiPaise", rs.getLong(5), "cardPaise", rs.getLong(6)), tenant(), outletId, Date.valueOf(from), Date.valueOf(to));
        long orders = days.stream().mapToLong(v -> ((Number) v.get("ordersCount")).longValue()).sum();
        long gmv = days.stream().mapToLong(v -> ((Number) v.get("gmvPaise")).longValue()).sum();
        return Map.of("from", from.toString(), "to", to.toString(), "ordersCount", orders, "gmvPaise", gmv, "averageOrderPaise", orders == 0 ? 0 : gmv / orders, "days", days);
    }

    public List<Map<String, Object>> topItems(UUID outletId, LocalDate from, LocalDate to) {
        requireRange(from, to); requireOutlet(outletId);
        return jdbc.query("select l.name,sum(l.qty) qty,sum(l.line_paise) sales from invoice_lines l join invoices i on i.id=l.invoice_id where i.tenant_id=? and i.outlet_id=? and i.status='PAID' and i.created_at >= ? and i.created_at < ? group by l.name order by sales desc limit 10", (rs, n) -> Map.<String, Object>of("name", rs.getString(1), "qty", rs.getBigDecimal(2).stripTrailingZeros().toPlainString(), "salesPaise", rs.getLong(3)), tenant(), outletId, Date.valueOf(from).toLocalDate().atStartOfDay(), Date.valueOf(to.plusDays(1)).toLocalDate().atStartOfDay());
    }

    public List<Map<String, Object>> paymentMix(UUID outletId, LocalDate from, LocalDate to) {
        requireRange(from, to); requireOutlet(outletId);
        return jdbc.query("select p.method,sum(p.amount_paise) amount from payments p join invoices i on i.id=p.invoice_id where p.tenant_id=? and i.outlet_id=? and p.status='SUCCESS' and p.created_at >= ? and p.created_at < ? group by p.method order by amount desc", (rs, n) -> Map.<String, Object>of("method", rs.getString(1), "amountPaise", rs.getLong(2)), tenant(), outletId, Date.valueOf(from).toLocalDate().atStartOfDay(), Date.valueOf(to.plusDays(1)).toLocalDate().atStartOfDay());
    }

    public List<Map<String, Object>> alerts(UUID outletId) {
        requireOutlet(outletId);
        return jdbc.query("select i.name,coalesce(sum(b.qty),0) qty,i.reorder_level,i.unit from inventory_items i left join stock_balances b on b.inventory_item_id=i.id and b.tenant_id=i.tenant_id and b.outlet_id=i.outlet_id where i.tenant_id=? and i.outlet_id=? and i.active=true group by i.id,i.name,i.reorder_level,i.unit having coalesce(sum(b.qty),0) <= i.reorder_level order by qty asc limit 10", (rs, n) -> Map.<String, Object>of("type", "LOW_STOCK", "title", rs.getString(1), "quantity", rs.getBigDecimal(2).stripTrailingZeros().toPlainString(), "reorderLevel", rs.getBigDecimal(3).stripTrailingZeros().toPlainString(), "unit", rs.getString(4)), tenant(), outletId);
    }

    /** Tenant-level operational history is reserved for owners because older audit events are not outlet-labelled. */
    public List<Map<String, Object>> activity(int limit) {
        var p = TenantContext.require();
        if (p.isGuest() || !p.hasRole("OWNER")) throw ApiException.forbidden("OWNER_ONLY", "Only restaurant owners can view operational history");
        int bound = Math.max(1, Math.min(limit, 100));
        return jdbc.query("select a.id,a.action,a.entity_type,a.entity_id,a.detail,a.created_at,u.name from audit_log a left join users u on u.id=a.actor_id where a.tenant_id=? order by a.created_at desc limit ?", (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getObject(1, UUID.class)); row.put("action", rs.getString(2)); row.put("entityType", rs.getString(3)); row.put("entityId", rs.getObject(4, UUID.class)); row.put("detail", rs.getString(5)); row.put("createdAt", rs.getTimestamp(6).toInstant().toString()); row.put("actorName", rs.getString(7) == null ? "System" : rs.getString(7));
            return row;
        }, p.tenantId(), bound);
    }

    public Map<String, Object> daily(UUID outletId, LocalDate date) {
        requireOutlet(outletId);
        List<Map<String, Object>> rows = jdbc.query("select orders_count,gmv_paise,cash_paise,upi_paise,card_paise from outlet_daily_sales where tenant_id=? and outlet_id=? and business_date=?", (rs, n) -> Map.<String, Object>of("ordersCount", rs.getLong(1), "gmvPaise", rs.getLong(2), "cashPaise", rs.getLong(3), "upiPaise", rs.getLong(4), "cardPaise", rs.getLong(5)), tenant(), outletId, Date.valueOf(date));
        return rows.isEmpty() ? Map.of("ordersCount", 0L, "gmvPaise", 0L, "cashPaise", 0L, "upiPaise", 0L, "cardPaise", 0L) : rows.getFirst();
    }

    private long number(String sql, UUID outletId) { Long value = jdbc.queryForObject(sql, Long.class, tenant(), outletId); return value == null ? 0 : value; }
    private UUID tenant() { return TenantContext.require().tenantId(); }
    private void requireOutlet(UUID outletId) { var p = TenantContext.require(); if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Guests cannot view reports"); if (p.outletIds() == null || !p.outletIds().contains(outletId)) throw ApiException.forbidden("OUTLET_ACCESS", "You do not have access to this outlet"); }
    private static void requireRange(LocalDate from, LocalDate to) { if (from == null || to == null || to.isBefore(from) || from.plusDays(92).isBefore(to)) throw ApiException.bad("REPORT_RANGE", "Choose a valid date range up to 93 days"); }
}
