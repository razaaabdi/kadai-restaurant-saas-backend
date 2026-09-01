package com.restaurant.onboarding.application;

import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import com.restaurant.platform.api.AuditWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@Service
public class OwnerAccountService {
	private final JdbcTemplate jdbc;
	private final AuditWriter audit;
	public OwnerAccountService(JdbcTemplate jdbc, AuditWriter audit) { this.jdbc = jdbc; this.audit = audit; }
	@Transactional(readOnly=true) public List<Map<String,Object>> outlets(){var p=owner();return jdbc.query("select id,slug as code,name,city,status from outlets where tenant_id=? order by name",(r,n)->Map.of("id",r.getObject("id"),"code",r.getString("code"),"name",r.getString("name"),"city",r.getString("city")==null?"":r.getString("city"),"status",r.getString("status")),p.tenantId());}
	@Transactional public Map<String,Object> createOutlet(Map<String,Object> body){
		var p=owner(); String name=required(body,"name"), code=code(required(body,"code"));
		var limit=jdbc.queryForMap("select coalesce(s.max_outlets_override,sp.max_outlets) max_outlets,(select count(*) from outlets where tenant_id=s.tenant_id) used from subscriptions s join subscription_plans sp on sp.id=s.plan_id where s.tenant_id=? for update of s",p.tenantId());
		if(((Number)limit.get("used")).intValue()>=((Number)limit.get("max_outlets")).intValue())throw ApiException.conflict("OUTLET_LIMIT_REACHED","Your subscription outlet limit has been reached");
		Integer duplicate=jdbc.queryForObject("select count(*) from outlets where tenant_id=? and lower(slug)=lower(?)",Integer.class,p.tenantId(),code);
		if(duplicate!=null&&duplicate>0)throw ApiException.conflict("OUTLET_CODE_EXISTS","A branch with this code already exists");
		UUID brandId=jdbc.query("select id from brands where tenant_id=? order by id limit 1",(r,n)->r.getObject(1,UUID.class),p.tenantId()).stream().findFirst().orElseThrow(()->ApiException.notFound("BRAND","Restaurant brand not found"));
		UUID id=UUID.randomUUID(); String timezone=optional(body,"timezone","Asia/Kolkata");
		jdbc.update("insert into outlets(id,tenant_id,brand_id,name,slug,timezone,status,address,city,state,country,contact_number) values(?,?,?,?,?,?,'ACTIVE',?,?,?,?,?)",id,p.tenantId(),brandId,name,code,timezone,optional(body,"address",""),optional(body,"city",""),optional(body,"state",""),optional(body,"country","India"),optional(body,"contactNumber",""));
		jdbc.update("insert into user_outlets(tenant_id,user_id,outlet_id) select ?,u.id,? from users u join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id where u.tenant_id=? and r.code='OWNER' on conflict do nothing",p.tenantId(),id,p.tenantId());
		audit.write("OUTLET_CREATED","OUTLET",id,"code="+code+" name="+name);
		return Map.of("id",id,"code",code,"name",name,"city",optional(body,"city",""),"status","ACTIVE");
	}
	@Transactional(readOnly=true) public Map<String,Object> subscription(){var p=owner();var rows=jdbc.query("select p.name,s.status,s.start_date,s.end_date,s.grace_period_end_date,coalesce(s.max_outlets_override,p.max_outlets) max_outlets,p.max_users,(select count(*) from outlets where tenant_id=s.tenant_id) outlet_usage,(select count(*) from users where tenant_id=s.tenant_id) user_usage from subscriptions s join subscription_plans p on p.id=s.plan_id where s.tenant_id=?",(r,n)->{LocalDate end=r.getDate("end_date").toLocalDate();Map<String,Object> v=new java.util.LinkedHashMap<>();v.put("planName",r.getString("name"));v.put("status",effective(r.getString("status"),end));v.put("startDate",r.getDate("start_date").toLocalDate().toString());v.put("endDate",end.toString());v.put("daysRemaining",ChronoUnit.DAYS.between(LocalDate.now(),end));v.put("outletUsage",r.getInt("outlet_usage"));v.put("maxOutlets",r.getInt("max_outlets"));v.put("userUsage",r.getInt("user_usage"));v.put("maxUsers",r.getInt("max_users"));if(r.getDate("grace_period_end_date")!=null)v.put("gracePeriodEndDate",r.getDate("grace_period_end_date").toLocalDate().toString());return v;},p.tenantId());if(rows.isEmpty())throw ApiException.notFound("SUBSCRIPTION","No subscription is assigned to this restaurant");return rows.getFirst();}
	@Transactional(readOnly=true) public List<String> features(){var p=owner();return jdbc.queryForList("select jsonb_array_elements_text(p.features) from subscriptions s join subscription_plans p on p.id=s.plan_id where s.tenant_id=?",String.class,p.tenantId());}
	private TenantPrincipal owner(){var p=TenantContext.require();if(!p.hasRole("OWNER"))throw ApiException.forbidden("OWNER_ONLY","Restaurant owner access is required");return p;}
	private static String required(Map<String,Object> body,String key){String value=body.get(key)==null?"":String.valueOf(body.get(key)).trim();if(value.isEmpty())throw ApiException.bad("OUTLET_REQUIRED",key+" is required");return value;}
	private static String optional(Map<String,Object> body,String key,String fallback){String value=body.get(key)==null?"":String.valueOf(body.get(key)).trim();return value.isEmpty()?fallback:value;}
	private static String code(String value){String normalized=value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");if(normalized.length()<2)throw ApiException.bad("OUTLET_CODE","Branch code must contain at least two letters or numbers");return normalized;}
	private static String effective(String stored,LocalDate end){if(end.isBefore(LocalDate.now())&&!List.of("SUSPENDED","CANCELLED").contains(stored))return "EXPIRED";if(!end.isBefore(LocalDate.now())&&!end.isAfter(LocalDate.now().plusDays(30))&&"ACTIVE".equals(stored))return "EXPIRING_SOON";return stored;}
}
