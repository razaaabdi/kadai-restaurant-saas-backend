package com.restaurant.onboarding.application;

import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.SubscriptionPolicy;
import com.restaurant.platform.api.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SubscriptionLifecycleService implements SubscriptionPolicy {
	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final boolean legacyOnboardingEnabled;

	public SubscriptionLifecycleService(JdbcTemplate jdbc, TransactionTemplate transactions,
			@Value("${app.legacy-onboarding-enabled:false}") boolean legacyOnboardingEnabled) {
		this.jdbc=jdbc; this.transactions=transactions; this.legacyOnboardingEnabled=legacyOnboardingEnabled;
	}

	@Override
	@Transactional(readOnly=true)
	public void assertNewWorkAllowed(UUID tenantId) {
		List<Map<String,Object>> rows=jdbc.query("select t.status tenant_status,s.status subscription_status,s.start_date,s.end_date from tenants t left join subscriptions s on s.tenant_id=t.id where t.id=?",
				(r,n)->Map.of("tenantStatus",r.getString("tenant_status"),"subscriptionStatus",r.getString("subscription_status")==null?"":r.getString("subscription_status"),"startDate",r.getDate("start_date")==null?"":r.getDate("start_date").toLocalDate(),"endDate",r.getDate("end_date")==null?"":r.getDate("end_date").toLocalDate()),tenantId);
		if(rows.isEmpty())throw ApiException.notFound("TENANT","Restaurant not found");
		Map<String,Object> row=rows.getFirst(); String tenantStatus=String.valueOf(row.get("tenantStatus"));
		if(List.of("SUSPENDED","DISABLED","CLOSED","SUBSCRIPTION_EXPIRED").contains(tenantStatus))throw ApiException.forbidden("RESTAURANT_INACTIVE","This restaurant cannot start new work");
		if(row.get("endDate") instanceof String) { if(legacyOnboardingEnabled)return; throw ApiException.forbidden("SUBSCRIPTION_REQUIRED","An active subscription is required"); }
		LocalDate start=(LocalDate)row.get("startDate"),end=(LocalDate)row.get("endDate"); String status=String.valueOf(row.get("subscriptionStatus")); LocalDate today=LocalDate.now();
		if(today.isBefore(start))throw ApiException.forbidden("SUBSCRIPTION_NOT_STARTED","The subscription has not started");
		if(today.isAfter(end)||List.of("EXPIRED","GRACE_PERIOD","SUSPENDED","CANCELLED").contains(status))throw ApiException.forbidden("SUBSCRIPTION_EXPIRED","The subscription has expired; existing orders may still be completed");
	}

	@Scheduled(cron="${app.subscription.lifecycle-cron:0 5 * * * *}")
	public void processLifecycle(){
		TenantContext.bootstrap(true);
		try{transactions.executeWithoutResult(tx->{LocalDate today=LocalDate.now();List<Map<String,Object>> rows=jdbc.query("select id,tenant_id,plan_id,status,start_date,end_date,grace_period_end_date from subscriptions for update",(r,n)->{Map<String,Object> x=new java.util.HashMap<>();x.put("id",r.getObject("id",UUID.class));x.put("tenantId",r.getObject("tenant_id",UUID.class));x.put("planId",r.getObject("plan_id",UUID.class));x.put("status",r.getString("status"));x.put("start",r.getDate("start_date").toLocalDate());x.put("end",r.getDate("end_date").toLocalDate());x.put("grace",r.getDate("grace_period_end_date")==null?null:r.getDate("grace_period_end_date").toLocalDate());return x;});for(var row:rows)transition(row,today);});}
		finally{TenantContext.bootstrap(false);}
	}

	private void transition(Map<String,Object> row,LocalDate today){UUID id=(UUID)row.get("id"),tenant=(UUID)row.get("tenantId"),plan=(UUID)row.get("planId");String old=String.valueOf(row.get("status"));LocalDate start=(LocalDate)row.get("start"),end=(LocalDate)row.get("end"),grace=(LocalDate)row.get("grace");if(grace==null)grace=end.plusDays(7);String next;if(today.isBefore(start))next="TRIAL";else if(!today.isAfter(end))next=!today.isBefore(end.minusDays(30))?"EXPIRING_SOON":"ACTIVE";else if(!today.isAfter(grace))next="GRACE_PERIOD";else next="EXPIRED";if(List.of("SUSPENDED","CANCELLED").contains(old))return;if(!old.equals(next)){jdbc.update("update subscriptions set status=?,grace_period_end_date=?,version=version+1,updated_at=now() where id=?",next,Date.valueOf(grace),id);jdbc.update("insert into subscription_history(id,tenant_id,subscription_id,action,old_plan_id,new_plan_id,old_end_date,new_end_date,reason) values(gen_random_uuid(),?,?,?, ?,?,?,?,'Automatic lifecycle transition')",tenant,id,"STATUS_"+next,plan,plan,Date.valueOf(end),Date.valueOf(end));jdbc.update("insert into audit_log(id,tenant_id,action,entity_type,entity_id,detail) values(gen_random_uuid(),?,'SUBSCRIPTION_STATUS_CHANGED','SUBSCRIPTION',?,?)",tenant,id,old+" -> "+next);}if(today.isAfter(end))jdbc.update("update tenants set status='SUBSCRIPTION_EXPIRED',version=version+1 where id=? and status='ACTIVE'",tenant);}
}
