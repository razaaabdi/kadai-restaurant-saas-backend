package com.restaurant.reporting.application;

import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID outletId, LocalDate from, LocalDate to) {
        requireRange(from, to); requireOutlet(outletId);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1), previousFrom = previousTo.minusDays(days - 1);
        String zone = timezone(outletId);
        long sales = paid(outletId, zone, from, to), previousSales = paid(outletId, zone, previousFrom, previousTo);
        long orders = orders(outletId, from, to), previousOrders = orders(outletId, previousFrom, previousTo);
        long activeTables = number("select count(*) from tables where tenant_id=? and outlet_id=? and deleted=false and status in ('OCCUPIED','BILL_REQUESTED')", outletId);
        long totalTables = number("select count(*) from tables where tenant_id=? and outlet_id=? and deleted=false", outletId);
        String userName = jdbc.query("select name from users where tenant_id=? and id=?", (rs, n) -> rs.getString(1), tenant(), TenantContext.require().userId()).stream().findFirst().orElse("Team");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("fromDate", from.toString()); result.put("toDate", to.toString()); result.put("userName", userName);
        result.put("sales", metric(sales, previousSales)); result.put("orders", metric(orders, previousOrders));
        result.put("activeTables", Map.of("count", activeTables, "total", totalTables));
        result.put("ratingAvailable", false);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> overview(UUID outletId, LocalDate from, LocalDate to) {
        requireRange(from, to); requireOutlet(outletId); String zone = timezone(outletId);
        boolean oneDay = from.equals(to);
        String bucket = oneDay ? "date_trunc('hour', p.created_at at time zone ?)" : "date_trunc('day', p.created_at at time zone ?)";
        String salesSql = "select " + bucket + " bucket,sum(p.amount_paise) value from payments p join invoices i on i.id=p.invoice_id where p.tenant_id=? and i.outlet_id=? and p.status='SUCCESS' and (p.created_at at time zone ?)::date between ? and ? group by bucket order by bucket";
        String orderBucket = oneDay ? "date_trunc('hour', o.created_at at time zone ?)" : "date_trunc('day', o.created_at at time zone ?)";
        String orderSql = "select " + orderBucket + " bucket,count(*) value from orders o where o.tenant_id=? and o.outlet_id=? and o.business_date between ? and ? group by bucket order by bucket";
        TreeMap<String,long[]> points = new TreeMap<>();
        jdbc.query(salesSql, (RowCallbackHandler) rs -> points.computeIfAbsent(rs.getTimestamp(1).toLocalDateTime().toString(), k -> new long[2])[0] = rs.getLong(2), zone, tenant(), outletId, zone, Date.valueOf(from), Date.valueOf(to));
        jdbc.query(orderSql, (RowCallbackHandler) rs -> points.computeIfAbsent(rs.getTimestamp(1).toLocalDateTime().toString(), k -> new long[2])[1] = rs.getLong(2), zone, tenant(), outletId, Date.valueOf(from), Date.valueOf(to));
        List<Map<String,Object>> rows = points.entrySet().stream().map(entry -> Map.<String,Object>of("timestamp", entry.getKey(), "salesAmountPaise", entry.getValue()[0], "orderCount", entry.getValue()[1])).toList();
        return Map.of("granularity", oneDay ? "HOUR" : "DAY", "timezone", zone, "points", rows);
    }

    @Transactional(readOnly = true)
    public Map<String,Object> orderStatus(UUID outletId, LocalDate from, LocalDate to) {
        requireRange(from, to); requireOutlet(outletId);
        List<Map<String,Object>> rows = jdbc.query("""
            select status,count(*) value from (select case
              when o.status in ('DRAFT','CONFIRMED','KOT_SENT') then 'NEW'
              when exists(select 1 from kots k where k.order_id=o.id and k.status in ('READY','PARTIALLY_READY')) then 'READY'
              when o.status='PREPARING' or exists(select 1 from kots k where k.order_id=o.id and k.status in ('ACCEPTED','PREPARING','PARTIALLY_PREPARING')) then 'PREPARING'
              when o.status in ('SERVED','BILL_REQUESTED','BILLED','PAID','COMPLETED') then 'SERVED'
              else 'NEW' end status
            from orders o where o.tenant_id=? and o.outlet_id=? and o.business_date between ? and ? and o.status not in ('CANCELLED','VOIDED')
            ) classified group by status
            """, (rs,n) -> Map.<String,Object>of("status",rs.getString(1),"count",rs.getLong(2)), tenant(),outletId,Date.valueOf(from),Date.valueOf(to));
        Map<String,Long> counts = new LinkedHashMap<>(); counts.put("NEW",0L); counts.put("PREPARING",0L); counts.put("READY",0L); counts.put("SERVED",0L);
        rows.forEach(row -> counts.put(String.valueOf(row.get("status")), ((Number)row.get("count")).longValue()));
        return Map.of("counts", counts);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> recentOrders(UUID outletId, LocalDate from, LocalDate to, int requestedLimit) {
        requireRange(from, to); requireOutlet(outletId); int limit = Math.max(1, Math.min(requestedLimit, 50));
        return jdbc.query("""
            select o.id,coalesce(o.order_number,upper(substr(o.id::text,1,8))),coalesce(o.order_type,case when o.table_id is null then 'TAKEAWAY' else 'DINE_IN' end),
              coalesce(o.customer_name,case when o.table_id is null then 'Walk-in customer' else 'Table guest' end),o.customer_phone,o.status,o.created_at,
              coalesce(i.total_paise,o.total_paise,o.subtotal_paise,0),o.table_id,t.code,o.token_number,i.id,i.invoice_number,i.status
            from orders o left join tables t on t.id=o.table_id left join lateral(select x.* from invoices x where x.order_id=o.id order by x.created_at desc limit 1)i on true
            where o.tenant_id=? and o.outlet_id=? and o.business_date between ? and ? order by o.created_at desc limit ?
            """, (rs,n) -> { Map<String,Object> row=new LinkedHashMap<>(); row.put("id",rs.getObject(1,UUID.class));row.put("orderNumber",rs.getString(2));row.put("orderType",rs.getString(3));row.put("customerName",rs.getString(4));row.put("customerPhone",rs.getString(5));row.put("status",rs.getString(6));row.put("createdAt",rs.getTimestamp(7).toInstant().toString());row.put("totalPaise",rs.getLong(8));row.put("tableId",rs.getObject(9,UUID.class));row.put("tableCode",rs.getString(10));row.put("tokenNumber",rs.getString(11));row.put("invoiceId",rs.getObject(12,UUID.class));row.put("invoiceNumber",rs.getString(13));row.put("invoiceStatus",rs.getString(14));return row;}, tenant(),outletId,Date.valueOf(from),Date.valueOf(to),limit);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> search(UUID outletId, String raw) {
        requireOutlet(outletId); String q = raw == null ? "" : raw.trim(); if(q.length()<2)return List.of(); if(q.length()>80)throw ApiException.bad("SEARCH","Search must be 80 characters or fewer"); String like="%"+q+"%";
        List<Map<String,Object>> results = new ArrayList<>();
        results.addAll(jdbc.query("""
            select o.id,coalesce(o.order_number,upper(substr(o.id::text,1,8))) ref,coalesce(o.customer_name,'Walk-in customer') label,coalesce(t.code,o.token_number,'') context
            from orders o left join tables t on t.id=o.table_id where o.tenant_id=? and o.outlet_id=? and (o.order_number ilike ? or o.customer_name ilike ? or o.customer_phone ilike ? or o.token_number ilike ? or t.code ilike ?) order by o.created_at desc limit 6
            """,(rs,n)->searchRow("ORDER",rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4)),tenant(),outletId,like,like,like,like,like));
        results.addAll(jdbc.query("select i.id,coalesce(i.invoice_number,upper(substr(i.id::text,1,8))) from invoices i where i.tenant_id=? and i.outlet_id=? and i.invoice_number ilike ? order by i.created_at desc limit 4",(rs,n)->searchRow("INVOICE",rs.getObject(1,UUID.class),rs.getString(2),"Invoice",null),tenant(),outletId,like));
        results.addAll(jdbc.query("select v.id,mi.name,v.name from variants v join items mi on mi.id=v.item_id where v.tenant_id=? and mi.outlet_id=? and mi.deleted=false and (mi.name ilike ? or v.name ilike ?) order by mi.name limit 4",(rs,n)->searchRow("MENU_ITEM",rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),null),tenant(),outletId,like,like));
        return results.stream().limit(12).toList();
    }

    private Map<String,Object> searchRow(String type,UUID id,String reference,String label,String context){Map<String,Object> row=new LinkedHashMap<>();row.put("type",type);row.put("id",id);row.put("reference",reference);row.put("label",label);row.put("context",context);return row;}
    private long paid(UUID outletId,String zone,LocalDate from,LocalDate to){Long value=jdbc.queryForObject("select coalesce(sum(p.amount_paise),0) from payments p join invoices i on i.id=p.invoice_id where p.tenant_id=? and i.outlet_id=? and p.status='SUCCESS' and (p.created_at at time zone ?)::date between ? and ?",Long.class,tenant(),outletId,zone,Date.valueOf(from),Date.valueOf(to));return value==null?0:value;}
    private long orders(UUID outletId,LocalDate from,LocalDate to){Long value=jdbc.queryForObject("select count(*) from orders where tenant_id=? and outlet_id=? and business_date between ? and ? and status not in ('CANCELLED','VOIDED')",Long.class,tenant(),outletId,Date.valueOf(from),Date.valueOf(to));return value==null?0:value;}
    private Map<String,Object> metric(long value,long previous){Map<String,Object> result=new LinkedHashMap<>();Double percentage=null;if(previous==0&&value==0)percentage=0.0;else if(previous!=0)percentage=Math.round(((value-previous)*1000.0/previous))/10.0;result.put("value",value);result.put("previousValue",previous);result.put("percentageChange",percentage);return result;}
    private long number(String sql,UUID outletId){Long value=jdbc.queryForObject(sql,Long.class,tenant(),outletId);return value==null?0:value;}
    private String timezone(UUID outletId){return jdbc.queryForObject("select timezone from outlets where tenant_id=? and id=?",String.class,tenant(),outletId);}
    private UUID tenant(){return TenantContext.require().tenantId();}
    private void requireOutlet(UUID outletId){var p=TenantContext.require();if(p.isGuest())throw ApiException.forbidden("STAFF_ONLY","Staff access is required");if(p.outletIds()==null||!p.outletIds().contains(outletId))throw ApiException.forbidden("OUTLET_ACCESS","You do not have access to this outlet");}
    private static void requireRange(LocalDate from,LocalDate to){if(from==null||to==null||to.isBefore(from)||from.plusDays(92).isBefore(to))throw ApiException.bad("DASHBOARD_RANGE","Choose a valid date range up to 93 days");}
}
