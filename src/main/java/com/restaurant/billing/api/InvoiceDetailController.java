package com.restaurant.billing.api;

import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceDetailController {
    private final JdbcTemplate jdbc;

    public InvoiceDetailController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/{invoiceId}")
    public Map<String, Object> detail(@PathVariable UUID invoiceId) {
        var principal = TenantContext.require();
        if (principal.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Guests cannot view invoice history");
        Map<String, Object> invoice = jdbc.query("""
                select i.invoice_number,i.status,i.subtotal_paise,i.discount_paise,i.service_charge_paise,
                       i.packaging_paise,i.tax_paise,i.rounding_paise,i.total_paise,i.created_at,i.order_id,i.outlet_id,o.table_id
                from invoices i join orders o on o.id=i.order_id where i.id=? and i.tenant_id=?
                """, (rs, n) -> {
            UUID outletId = rs.getObject(12, UUID.class);
            if (principal.outletIds() == null || !principal.outletIds().contains(outletId))
                throw ApiException.forbidden("OUTLET_ACCESS", "You do not have access to this outlet");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", invoiceId); row.put("invoiceNumber", rs.getString(1)); row.put("status", rs.getString(2));
            row.put("subtotalPaise", rs.getLong(3)); row.put("discountPaise", rs.getLong(4)); row.put("serviceChargePaise", rs.getLong(5));
            row.put("packagingPaise", rs.getLong(6)); row.put("taxPaise", rs.getLong(7)); row.put("roundingPaise", rs.getLong(8));
            row.put("totalPaise", rs.getLong(9)); row.put("createdAt", rs.getTimestamp(10).toInstant().toString());
            row.put("orderId", rs.getObject(11, UUID.class)); row.put("outletId", outletId); row.put("tableId", rs.getObject(13, UUID.class));
            return row;
        }, invoiceId, principal.tenantId()).stream().findFirst().orElseThrow(() -> ApiException.notFound("INVOICE", "Invoice not found"));
        List<Map<String, Object>> lines = jdbc.query("select id,name,qty,unit_paise,line_paise,tax_paise from invoice_lines where tenant_id=? and invoice_id=? order by id",
                (rs, n) -> Map.<String, Object>of("id", rs.getObject(1, UUID.class), "name", rs.getString(2), "qty", rs.getBigDecimal(3).stripTrailingZeros().toPlainString(), "unitPaise", rs.getLong(4), "linePaise", rs.getLong(5), "taxPaise", rs.getLong(6)), principal.tenantId(), invoiceId);
        List<Map<String, Object>> payments = jdbc.query("select id,method,amount_paise,change_paise,status,created_at from payments where tenant_id=? and invoice_id=? order by created_at",
                (rs, n) -> Map.<String, Object>of("id", rs.getObject(1, UUID.class), "method", rs.getString(2), "amountPaise", rs.getLong(3), "changePaise", rs.getLong(4), "status", rs.getString(5), "createdAt", rs.getTimestamp(6).toInstant().toString()), principal.tenantId(), invoiceId);
        List<Map<String, Object>> amendments = jdbc.query("""
                select a.id,a.amendment_number,a.type,a.reason_code,a.reason_text,a.status,a.previous_total_paise,a.new_total_paise,
                       a.created_at,a.approved_at,a.applied_at,requester.name,approver.name
                from invoice_amendments a join users requester on requester.id=a.requested_by left join users approver on approver.id=a.approved_by
                where a.tenant_id=? and a.invoice_id=? order by a.amendment_number
                """, (rs, n) -> { Map<String, Object> row = new LinkedHashMap<>(); row.put("id", rs.getObject(1, UUID.class)); row.put("amendmentNumber", rs.getInt(2)); row.put("type", rs.getString(3)); row.put("reasonCode", rs.getString(4)); row.put("reasonText", rs.getString(5)); row.put("status", rs.getString(6)); row.put("previousTotalPaise", rs.getLong(7)); row.put("newTotalPaise", rs.getObject(8)); row.put("createdAt", rs.getTimestamp(9).toInstant().toString()); row.put("approvedAt", instant(rs.getTimestamp(10))); row.put("appliedAt", instant(rs.getTimestamp(11))); row.put("requestedBy", rs.getString(12)); row.put("approvedBy", rs.getString(13)); return row; }, principal.tenantId(), invoiceId);
        List<Map<String, Object>> audit = jdbc.query("select id,action,detail,created_at,actor_id from audit_log where tenant_id=? and entity_type='INVOICE' and entity_id=? order by created_at",
                (rs, n) -> { Map<String, Object> row = new LinkedHashMap<>(); row.put("id", rs.getObject(1, UUID.class)); row.put("action", rs.getString(2)); row.put("detail", rs.getString(3)); row.put("createdAt", rs.getTimestamp(4).toInstant().toString()); row.put("actorId", rs.getObject(5, UUID.class)); return row; }, principal.tenantId(), invoiceId);
        return Map.of("invoice", invoice, "lines", lines, "payments", payments, "amendments", amendments, "audit", audit);
    }

    private static String instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant().toString(); }
}
