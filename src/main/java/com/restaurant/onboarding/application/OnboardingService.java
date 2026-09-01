package com.restaurant.onboarding.application;

import com.restaurant.identity.api.IdentityFacade;
import com.restaurant.organization.api.OrganizationFacade;
import com.restaurant.outlet.api.OutletFacade;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OnboardingService {
	private final OrganizationFacade org;
	private final OutletFacade outlets;
	private final IdentityFacade identity;
	private final boolean legacyEnabled;

	public OnboardingService(OrganizationFacade org, OutletFacade outlets, IdentityFacade identity,
			@Value("${app.legacy-onboarding-enabled:false}") boolean legacyEnabled) {
		this.org = org;
		this.outlets = outlets;
		this.identity = identity;
		this.legacyEnabled = legacyEnabled;
	}

	@Transactional
	public Map<String, Object> onboard(String restaurantName, String slug, String ownerEmail, String ownerPassword, String ownerName) {
		if (!legacyEnabled) throw ApiException.forbidden("PLATFORM_PROVISIONING_REQUIRED", "Restaurants must be provisioned by a platform administrator");
		TenantContext.bootstrap(true);
		String finalSlug = slug.toLowerCase().replaceAll("[^a-z0-9-]", "-");
		if (org.slugTaken(finalSlug)) {
			finalSlug = finalSlug + "-" + Integer.toHexString(restaurantName.hashCode() & 0xffff);
			if (org.slugTaken(finalSlug)) {
				throw ApiException.conflict("SLUG_TAKEN", "Slug collision, retry with a different slug");
			}
		}
		UUID tenantId = org.createTenant(restaurantName, finalSlug);
		TenantContext.set(new TenantPrincipal(tenantId, null, List.of(), Set.of("OWNER"), "staff", null, null, null, null));
		UUID brandId = org.createBrand(tenantId, restaurantName);
		UUID outletId = outlets.createCafeOutlet(tenantId, brandId, restaurantName + " Cafe");
		org.createProPlan(tenantId);
		UUID userId = identity.createOwner(tenantId, outletId, ownerEmail, ownerPassword, ownerName);
		return Map.of(
				"tenantId", tenantId,
				"slug", finalSlug,
				"outletId", outletId,
				"userId", userId,
				"plan", "PRO",
				"template", "CAFE",
				"qrOrderingEnabled", true,
				"kotEnabled", true
		);
	}
}
