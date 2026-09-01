package com.restaurant;

import com.restaurant.onboarding.application.OwnerAccountService;
import com.restaurant.onboarding.application.SubscriptionLifecycleService;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.PlatformRestaurantCommands;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import com.restaurant.platform.application.PlatformReadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformRestaurantManagementIT extends AbstractIT {
	@Autowired PlatformRestaurantCommands commands;
	@Autowired PlatformReadService reads;
	@Autowired OwnerAccountService ownerAccount;
	@Autowired SubscriptionLifecycleService lifecycle;
	@Autowired JdbcTemplate jdbc;

	@Test void provisionsAndManagesRestaurantSubscriptionAndOwnerReads() {
		UUID actor=UUID.randomUUID();
		UUID pro=UUID.fromString(String.valueOf(reads.plans().stream().filter(p->"PRO".equals(p.get("code"))).findFirst().orElseThrow().get("id")));
		Map<String,Object> body=new HashMap<>();
		body.put("legalName","Phase One Foods Private Limited"); body.put("displayName","Phase One Cafe"); body.put("restaurantType","CAFE");
		body.put("primaryContactName","Operations"); body.put("primaryContactEmail","ops-phase-one@example.com"); body.put("primaryContactPhone","9999999999");
		body.put("address","Main Road"); body.put("city","Pune"); body.put("state","Maharashtra"); body.put("country","India"); body.put("timezone","Asia/Kolkata"); body.put("currency","INR");
		body.put("planId",pro.toString()); body.put("subscriptionStartDate",LocalDate.now().toString()); body.put("subscriptionEndDate",LocalDate.now().plusYears(1).toString()); body.put("activate",true);
		body.put("initialOutlet",Map.of("name","Phase One Main","code","MAIN","city","Pune","state","Maharashtra","country","India","timezone","Asia/Kolkata"));
		body.put("owner",Map.of("name","Phase Owner","email","owner-phase-one@example.com","phone","9999999998"));

		Map<String,Object> created=commands.create(actor,body); UUID tenant=(UUID)created.get("tenantId");
		Map<String,Object> detail=reads.restaurant(tenant);
		assertThat(detail.get("subscriptionStatus")).isEqualTo("ACTIVE"); assertThat(detail.get("planName")).isEqualTo("Pro");
		assertThat((List<?>)detail.get("outlets")).hasSize(1); assertThat(created.get("ownerSetupToken")).isNotNull();

		commands.renew(actor,tenant,3,"Customer renewal",((Number)detail.get("subscriptionVersion")).longValue());
		detail=reads.restaurant(tenant); assertThat(((List<?>)detail.get("subscriptionHistory"))).hasSize(2);
		commands.changeTenantStatus(actor,tenant,"suspend","Payment review",((Number)detail.get("version")).longValue());
		assertThat(reads.restaurant(tenant).get("status")).isEqualTo("SUSPENDED");

		TenantContext.set(new TenantPrincipal(tenant,UUID.randomUUID(),List.of(),Set.of("OWNER"),"staff",null,null,null,null));
		assertThat(ownerAccount.outlets()).hasSize(1); assertThat(ownerAccount.subscription().get("planName")).isEqualTo("Pro");
		assertThat(ownerAccount.features()).contains("ORDER_MANAGEMENT","INVENTORY");
		Map<String,Object> suspended=reads.restaurant(tenant);
		commands.changeTenantStatus(actor,tenant,"activate","",((Number)suspended.get("version")).longValue());

		TenantContext.bootstrap(true);
		try{jdbc.update("update subscriptions set status='ACTIVE',start_date=current_date-30,end_date=current_date-1,grace_period_end_date=current_date+6 where tenant_id=?",tenant);}finally{TenantContext.bootstrap(false);}
		lifecycle.processLifecycle();
		assertThat(reads.restaurant(tenant).get("subscriptionStatus")).isEqualTo("GRACE_PERIOD");
		assertThat(reads.restaurant(tenant).get("status")).isEqualTo("SUBSCRIPTION_EXPIRED");
		assertThatThrownBy(() -> lifecycle.assertNewWorkAllowed(tenant)).isInstanceOf(ApiException.class).hasMessageContaining("cannot start new work");

		Map<String,Object> expired=reads.restaurant(tenant);
		commands.renew(actor,tenant,1,"Restore service",((Number)expired.get("subscriptionVersion")).longValue());
		lifecycle.assertNewWorkAllowed(tenant);
	}
}
