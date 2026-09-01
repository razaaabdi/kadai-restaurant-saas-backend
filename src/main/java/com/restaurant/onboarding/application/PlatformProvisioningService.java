package com.restaurant.onboarding.application;

import com.restaurant.platform.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PlatformProvisioningService implements PlatformRestaurantCommands {
	private static final List<String> ROLES = List.of("OWNER", "MANAGER", "SHIFT_MANAGER", "CASHIER", "WAITER", "KITCHEN", "INVENTORY_MANAGER");
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final PasswordEncoder encoder;
	private final PlatformTokenService tokens;

	public PlatformProvisioningService(JdbcTemplate jdbc, TransactionTemplate transactions, PasswordEncoder encoder,
			PlatformTokenService tokens) {
		this.jdbc = jdbc; this.transactions = transactions; this.encoder = encoder; this.tokens = tokens;
	}

	@Override public Map<String, Object> create(UUID actorId, Map<String, Object> body) {
		String legalName = required(body, "legalName");
		String displayName = required(body, "displayName");
		String contactEmail = email(required(body, "primaryContactEmail"));
		Map<String, Object> outlet = map(body, "initialOutlet");
		Map<String, Object> owner = map(body, "owner");
		UUID planId = uuid(body, "planId");
		LocalDate start = date(body, "subscriptionStartDate");
		LocalDate end = date(body, "subscriptionEndDate");
		if (end.isBefore(start)) throw ApiException.bad("SUBSCRIPTION_DATES", "Subscription end date cannot precede its start date");
		UUID tenantId = UUID.randomUUID(); UUID brandId = UUID.randomUUID(); UUID outletId = UUID.randomUUID(); UUID userId = UUID.randomUUID(); UUID subscriptionId = UUID.randomUUID();
		String setupToken = tokens.refreshToken();
		inBootstrapTransaction(() -> {
			Integer plan = jdbc.queryForObject("select count(*) from subscription_plans where id=? and active=true", Integer.class, planId);
			if (plan == null || plan == 0) throw ApiException.bad("PLAN", "Selected plan is unavailable");
			String slug = uniqueSlug(displayName);
			String tenantStatus = bool(body.get("activate"), true) ? "ACTIVE" : "PENDING_SETUP";
			jdbc.update("insert into tenants(id,name,slug,status,legal_name,display_name,restaurant_type,primary_contact_name,primary_contact_email,primary_contact_phone,address,city,state,country,currency) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
				tenantId, displayName, slug, tenantStatus, legalName, displayName, text(body,"restaurantType"), text(body,"primaryContactName"), contactEmail, text(body,"primaryContactPhone"), text(body,"address"), text(body,"city"), text(body,"state"), text(body,"country"), defaulted(body,"currency","INR"));
			jdbc.update("insert into brands(id,tenant_id,name) values(?,?,?)", brandId, tenantId, displayName);
			jdbc.update("insert into outlets(id,tenant_id,brand_id,name,slug,timezone,status,address,city,state,country,contact_number) values(?,?,?,?,?,?,?,?,?,?,?,?)", outletId, tenantId, brandId, required(outlet,"name"), normalizedCode(required(outlet,"code")), defaulted(outlet,"timezone","Asia/Kolkata"), tenantStatus.equals("ACTIVE")?"ACTIVE":"PENDING_SETUP", text(outlet,"address"), text(outlet,"city"), text(outlet,"state"), text(outlet,"country"), text(outlet,"contactNumber"));
			jdbc.update("insert into plans(id,tenant_id,code,inventory_enabled,multi_outlet) select gen_random_uuid(),?,code,jsonb_exists(features,'INVENTORY'),jsonb_exists(features,'MULTI_OUTLET') from subscription_plans where id=?", tenantId, planId);
			for (String role : ROLES) jdbc.update("insert into roles(id,tenant_id,code) values(gen_random_uuid(),?,?)", tenantId, role);
			String ownerEmail = email(required(owner,"email"));
			jdbc.update("insert into users(id,tenant_id,email,password_hash,name,status) values(?,?,?,?,?,'ACTIVE')", userId, tenantId, ownerEmail, encoder.encode(tokens.refreshToken()), required(owner,"name"));
			jdbc.update("insert into user_roles(tenant_id,user_id,role_id) select ?,?,id from roles where tenant_id=? and code='OWNER'", tenantId, userId, tenantId);
			jdbc.update("insert into user_outlets(tenant_id,user_id,outlet_id) values(?,?,?)", tenantId, userId, outletId);
			jdbc.update("insert into password_reset_tokens(id,tenant_id,user_id,token_hash,expires_at) values(gen_random_uuid(),?,?,?,?)", tenantId, userId, IdempotencyService.sha256(setupToken), Timestamp.from(Instant.now().plus(48, ChronoUnit.HOURS)));
			String subscriptionStatus = start.isAfter(LocalDate.now()) ? "TRIAL" : "ACTIVE";
			Integer override = body.get("maxOutletsOverride") instanceof Number n ? n.intValue() : null;
			jdbc.update("insert into subscriptions(id,tenant_id,plan_id,status,start_date,end_date,grace_period_end_date,max_outlets_override) values(?,?,?,?,?,?,?,?)", subscriptionId, tenantId, planId, subscriptionStatus, Date.valueOf(start), Date.valueOf(end), Date.valueOf(end.plusDays(7)), override);
			jdbc.update("insert into subscription_history(id,tenant_id,subscription_id,action,new_plan_id,new_end_date,reason,performed_by) values(gen_random_uuid(),?,?, 'CREATED',?,?,?,?)", tenantId, subscriptionId, planId, Date.valueOf(end), "Initial platform provisioning", actorId);
			audit(actorId, tenantId, "RESTAURANT_CREATED", "owner=" + ownerEmail + "; contact=" + contactEmail);
			return null;
		});
		return Map.of("tenantId",tenantId,"ownerSetupToken",setupToken);
	}

	@Override public void changeTenantStatus(UUID actorId, UUID tenantId, String action, String reason, long expected) {
		String status = switch (action) { case "activate" -> "ACTIVE"; case "suspend" -> "SUSPENDED"; case "disable" -> "DISABLED"; default -> throw ApiException.bad("STATUS", "Invalid tenant action"); };
		if (!"activate".equals(action) && reason.isBlank()) throw ApiException.bad("REASON", "A reason is required");
		inBootstrapTransaction(() -> { int changed=jdbc.update("update tenants set status=?,version=version+1 where id=? and version=?",status,tenantId,expected); if(changed==0) versionOrMissing("tenants",tenantId); audit(actorId,tenantId,"RESTAURANT_"+status,reason); return null; });
	}

	@Override public Map<String, Object> addOutlet(UUID actorId, UUID tenantId, Map<String, Object> body) {
		return inBootstrapTransaction(() -> {
			var limits=jdbc.queryForMap("select coalesce(s.max_outlets_override,p.max_outlets) max_outlets,(select count(*) from outlets where tenant_id=s.tenant_id) used from subscriptions s join subscription_plans p on p.id=s.plan_id where s.tenant_id=?",tenantId);
			if(((Number)limits.get("used")).intValue()>=((Number)limits.get("max_outlets")).intValue())throw ApiException.conflict("OUTLET_LIMIT","Subscription outlet limit reached");
			UUID id=UUID.randomUUID(); UUID brand=jdbc.queryForObject("select id from brands where tenant_id=? limit 1",UUID.class,tenantId);
			jdbc.update("insert into outlets(id,tenant_id,brand_id,name,slug,timezone,status,city) values(?,?,?,?,?,?,?,?)",id,tenantId,brand,required(body,"name"),normalizedCode(required(body,"code")),defaulted(body,"timezone","Asia/Kolkata"),bool(body.get("active"),true)?"ACTIVE":"PENDING_SETUP",text(body,"city"));
			audit(actorId,tenantId,"OUTLET_CREATED","outlet="+id); return outlet(id);
		});
	}

	@Override public Map<String, Object> changeOutletStatus(UUID actorId, UUID outletId, String action, String reason, long expected) {
		if (reason.isBlank()) throw ApiException.bad("REASON", "A reason is required"); String status="activate".equals(action)?"ACTIVE":"SUSPENDED";
		return inBootstrapTransaction(() -> { UUID tenant=jdbc.query("select tenant_id from outlets where id=?",(r,n)->r.getObject(1,UUID.class),outletId).stream().findFirst().orElseThrow(()->ApiException.notFound("OUTLET","Outlet not found")); int changed=jdbc.update("update outlets set status=?,version=version+1 where id=? and version=?",status,outletId,expected);if(changed==0)throw ApiException.conflict("VERSION_CONFLICT","Outlet changed; reload before saving");audit(actorId,tenant,"OUTLET_"+status,reason);return outlet(outletId);});
	}

	@Override public void renew(UUID actorId, UUID tenantId, int months, String reason, long expected) {
		if(months<1||months>120)throw ApiException.bad("RENEWAL_PERIOD","Renewal must be between 1 and 120 months");
		inBootstrapTransaction(() -> {var row=subscription(tenantId);if(((Number)row.get("version")).longValue()!=expected)throw ApiException.conflict("VERSION_CONFLICT","Subscription changed; reload before saving");LocalDate old=((Date)row.get("end_date")).toLocalDate();LocalDate next=(old.isAfter(LocalDate.now())?old:LocalDate.now()).plusMonths(months);jdbc.update("update subscriptions set end_date=?,grace_period_end_date=?,status='ACTIVE',version=version+1,updated_at=now() where tenant_id=?",Date.valueOf(next),Date.valueOf(next.plusDays(7)),tenantId);jdbc.update("update tenants set status='ACTIVE',version=version+1 where id=? and status='SUBSCRIPTION_EXPIRED'",tenantId);jdbc.update("insert into subscription_history(id,tenant_id,subscription_id,action,old_plan_id,new_plan_id,old_end_date,new_end_date,reason,performed_by) values(gen_random_uuid(),?,?,'RENEWED',?,?,?,?,?,?)",tenantId,row.get("id"),row.get("plan_id"),row.get("plan_id"),Date.valueOf(old),Date.valueOf(next),reason,actorId);audit(actorId,tenantId,"SUBSCRIPTION_RENEWED",reason);return null;});
	}

	@Override public void changePlan(UUID actorId, UUID tenantId, UUID planId, String reason, long expected) {
		inBootstrapTransaction(() -> {var row=subscription(tenantId);if(((Number)row.get("version")).longValue()!=expected)throw ApiException.conflict("VERSION_CONFLICT","Subscription changed; reload before saving");Integer found=jdbc.queryForObject("select count(*) from subscription_plans where id=? and active",Integer.class,planId);if(found==null||found==0)throw ApiException.bad("PLAN","Selected plan is unavailable");jdbc.update("update subscriptions set plan_id=?,version=version+1,updated_at=now() where tenant_id=?",planId,tenantId);jdbc.update("update plans set code=p.code,inventory_enabled=jsonb_exists(p.features,'INVENTORY'),multi_outlet=jsonb_exists(p.features,'MULTI_OUTLET') from subscription_plans p where plans.tenant_id=? and p.id=?",tenantId,planId);jdbc.update("insert into subscription_history(id,tenant_id,subscription_id,action,old_plan_id,new_plan_id,old_end_date,new_end_date,reason,performed_by) values(gen_random_uuid(),?,?,'PLAN_CHANGED',?,?,?,?,?,?)",tenantId,row.get("id"),row.get("plan_id"),planId,row.get("end_date"),row.get("end_date"),reason,actorId);audit(actorId,tenantId,"SUBSCRIPTION_PLAN_CHANGED",reason);return null;});
	}

	private Map<String,Object> subscription(UUID tenant){try{return jdbc.queryForMap("select id,plan_id,end_date,version from subscriptions where tenant_id=? for update",tenant);}catch(Exception e){throw ApiException.notFound("SUBSCRIPTION","Subscription not found");}}
	private Map<String,Object> outlet(UUID id){return jdbc.queryForMap("select id,tenant_id as \"tenantId\",slug as code,name,city,state,timezone,status,(status='ACTIVE') active,version from outlets where id=?",id);}
	private void versionOrMissing(String table,UUID id){Integer count=jdbc.queryForObject("select count(*) from "+table+" where id=?",Integer.class,id);if(count==null||count==0)throw ApiException.notFound("RESTAURANT","Restaurant not found");throw ApiException.conflict("VERSION_CONFLICT","Restaurant changed; reload before saving");}
	private void audit(UUID actor,UUID tenant,String action,String detail){jdbc.update("insert into audit_log(id,tenant_id,actor_id,action,entity_type,entity_id,detail) values(gen_random_uuid(),?,?,?,'TENANT',?,?)",tenant,actor,action,tenant,detail);}
	private <T>T inBootstrapTransaction(java.util.concurrent.Callable<T> work){TenantContext.bootstrap(true);try{return transactions.execute(x->{try{return work.call();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}});}catch(DataIntegrityViolationException e){throw ApiException.conflict("DUPLICATE","A restaurant, owner email, or outlet code already exists");}finally{TenantContext.bootstrap(false);}}
	private String uniqueSlug(String name){String base=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");if(base.isBlank())base="restaurant";String value=base;int i=2;while(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from tenants where slug=?)",Boolean.class,value)))value=base+"-"+i++;return value;}
	private static Map<String,Object> map(Map<String,Object>b,String key){Object value=b.get(key);if(value instanceof Map<?,?> m){Map<String,Object> out=new HashMap<>();m.forEach((k,v)->out.put(String.valueOf(k),v));return out;}throw ApiException.bad("FIELD",key+" is required");}
	private static String required(Map<String,Object>b,String key){String v=text(b,key);if(v.isBlank())throw ApiException.bad("FIELD",key+" is required");return v;}
	private static String text(Map<String,Object>b,String key){Object v=b.get(key);return v==null?"":String.valueOf(v).trim();}
	private static String defaulted(Map<String,Object>b,String key,String fallback){String v=text(b,key);return v.isBlank()?fallback:v;}
	private static String email(String value){String v=value.toLowerCase(Locale.ROOT);if(!v.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw ApiException.bad("EMAIL","Enter a valid email address");return v;}
	private static UUID uuid(Map<String,Object>b,String key){try{return UUID.fromString(required(b,key));}catch(Exception e){throw ApiException.bad("FIELD",key+" must be a UUID");}}
	private static LocalDate date(Map<String,Object>b,String key){try{return LocalDate.parse(required(b,key));}catch(Exception e){throw ApiException.bad("FIELD",key+" must be a date");}}
	private static boolean bool(Object value,boolean fallback){return value instanceof Boolean b?b:fallback;}
	private static String normalizedCode(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");}
}
