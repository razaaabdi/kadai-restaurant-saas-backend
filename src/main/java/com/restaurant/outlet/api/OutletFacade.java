package com.restaurant.outlet.api;

import com.restaurant.outlet.infrastructure.OutletEntity;
import com.restaurant.outlet.infrastructure.OutletRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OutletFacade {
	private final OutletRepository outlets;

	public OutletFacade(OutletRepository outlets) {
		this.outlets = outlets;
	}

	public UUID createCafeOutlet(UUID tenantId, UUID brandId, String name) {
		OutletEntity o = new OutletEntity();
		o.setTenantId(tenantId);
		o.setBrandId(brandId);
		o.setName(name);
		o.setSlug("main");
		outlets.save(o);
		return o.getId();
	}
}
