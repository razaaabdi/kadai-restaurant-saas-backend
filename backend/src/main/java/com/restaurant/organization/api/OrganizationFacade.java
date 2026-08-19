package com.restaurant.organization.api;

import com.restaurant.organization.infrastructure.BrandEntity;
import com.restaurant.organization.infrastructure.BrandRepository;
import com.restaurant.organization.infrastructure.PlanEntity;
import com.restaurant.organization.infrastructure.PlanRepository;
import com.restaurant.organization.infrastructure.TenantEntity;
import com.restaurant.organization.infrastructure.TenantRepository;
import com.restaurant.platform.api.ApiException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrganizationFacade {
	private final TenantRepository tenants;
	private final BrandRepository brands;
	private final PlanRepository plans;

	public OrganizationFacade(TenantRepository tenants, BrandRepository brands, PlanRepository plans) {
		this.tenants = tenants;
		this.brands = brands;
		this.plans = plans;
	}

	public UUID createTenant(String name, String slug) {
		if (tenants.existsBySlug(slug)) throw ApiException.conflict("SLUG_TAKEN", "Slug in use");
		TenantEntity t = new TenantEntity();
		t.setName(name);
		t.setSlug(slug);
		tenants.save(t);
		return t.getId();
	}

	public boolean slugTaken(String slug) {
		return tenants.existsBySlug(slug);
	}

	public UUID createBrand(UUID tenantId, String name) {
		BrandEntity b = new BrandEntity();
		b.setTenantId(tenantId);
		b.setName(name);
		brands.save(b);
		return b.getId();
	}

	public void createProPlan(UUID tenantId) {
		PlanEntity p = new PlanEntity();
		p.setTenantId(tenantId);
		p.setCode("PRO");
		p.setInventoryEnabled(true);
		plans.save(p);
	}
}
