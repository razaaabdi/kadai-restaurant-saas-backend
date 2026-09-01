package com.restaurant.kitchen.application;

import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.AuditWriter;
import com.restaurant.platform.api.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class KotPrintService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AuditWriter audit;
    private final boolean printerEnabled;

    public KotPrintService(JdbcTemplate jdbc, TransactionTemplate transactions, AuditWriter audit,
            @Value("${app.kot.printer-enabled:false}") boolean printerEnabled) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.audit = audit;
        this.printerEnabled = printerEnabled;
    }

    @Transactional
    public void queueInitial(UUID tenant, UUID outlet, UUID kot) {
        jdbc.update(
                "insert into kot_print_jobs(id,tenant_id,outlet_id,kot_id,kind,status) values(gen_random_uuid(),?,?,?,'INITIAL','PENDING') on conflict do nothing",
                tenant, outlet, kot);
        attemptLatest(kot);
    }

    @Transactional
    public Map<String, Object> reprint(UUID kotId, String reason) {
        if (reason == null || reason.isBlank())
            throw ApiException.bad("REPRINT_REASON", "A reprint reason is required");
        var row = jdbc.query("select tenant_id,outlet_id from kots where id=?",
                (r, n) -> Map.of("tenant", r.getObject(1, UUID.class), "outlet", r.getObject(2, UUID.class)), kotId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("KOT", "KOT not found"));
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into kot_print_jobs(id,tenant_id,outlet_id,kot_id,kind,status,reason) values(?,?,?,?, 'REPRINT','PENDING',?)",
                id, row.get("tenant"), row.get("outlet"), kotId, reason.trim());
        audit.write("KOT_REPRINT_REQUESTED", "KOT", kotId, reason.trim());
        attempt(id);
        return view(id);
    }

    @Transactional
    public Map<String, Object> retry(UUID kotId) {
        var id = jdbc.query(
                "select id from kot_print_jobs where kot_id=? and status='FAILED' order by created_at desc limit 1",
                (r, n) -> r.getObject(1, UUID.class), kotId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("PRINT_JOB", "No failed print job for this KOT"));
        attempt(id);
        return view(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(UUID kotId) {
        var rows = jdbc.query(
                "select id,kind,status,attempt_count,last_error,printed_at,created_at,reason from kot_print_jobs where kot_id=? order by created_at desc",
                (r, n) -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("id", r.getObject("id", UUID.class));
                    x.put("kind", r.getString("kind"));
                    x.put("status", r.getString("status"));
                    x.put("attemptCount", r.getInt("attempt_count"));
                    x.put("lastError", r.getString("last_error"));
                    x.put("reason", r.getString("reason"));
                    x.put("printedAt", r.getTimestamp("printed_at") == null ? null
                            : r.getTimestamp("printed_at").toInstant().toString());
                    return x;
                }, kotId);
        return Map.of("jobs", rows, "latest", rows.isEmpty() ? Map.of("status", "PENDING") : rows.getFirst());
    }

    @Scheduled(cron = "${app.kot.print-retry-cron:0 */5 * * * *}")
    public void retryFailed() {
        TenantContext.bootstrap(true);
        try {
            transactions.executeWithoutResult(x -> jdbc.query(
                    "select id from kot_print_jobs where status='FAILED' and attempt_count<5 order by created_at limit 25",
                    (r, n) -> r.getObject(1, UUID.class)).forEach(this::attempt));
        } finally {
            TenantContext.bootstrap(false);
        }
    }

    private void attemptLatest(UUID kot) {
        jdbc.query(
                "select id from kot_print_jobs where kot_id=? and status in ('PENDING','FAILED') order by created_at desc limit 1",
                (r, n) -> r.getObject(1, UUID.class), kot).stream().findFirst().ifPresent(this::attempt);
    }

    private void attempt(UUID id) {
        try {
            if (!printerEnabled)
                throw new IllegalStateException("No KOT printer is configured");
            jdbc.update(
                    "update kot_print_jobs set status='PRINTED',attempt_count=attempt_count+1,last_error=null,printed_at=now() where id=?",
                    id);
        } catch (Exception e) {
            jdbc.update(
                    "update kot_print_jobs set status='FAILED',attempt_count=attempt_count+1,last_error=? where id=?",
                    e.getMessage(), id);
        }
    }

    private Map<String, Object> view(UUID id) {
        return jdbc.query("select id,kind,status,attempt_count,last_error,printed_at from kot_print_jobs where id=?",
                (r, n) -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("id", r.getObject("id", UUID.class));
                    x.put("kind", r.getString("kind"));
                    x.put("status", r.getString("status"));
                    x.put("attemptCount", r.getInt("attempt_count"));
                    x.put("lastError", r.getString("last_error"));
                    x.put("printedAt", r.getTimestamp("printed_at") == null ? null
                            : r.getTimestamp("printed_at").toInstant().toString());
                    return x;
                }, id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("PRINT_JOB", "Print job not found"));
    }
}
