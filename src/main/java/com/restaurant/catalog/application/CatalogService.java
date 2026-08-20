package com.restaurant.catalog.application;

import com.restaurant.catalog.infrastructure.*;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogService {
	private final CategoryRepository categories;
	private final ItemRepository items;
	private final VariantRepository variants;
	private final TaxCodeRepository taxes;
	private final ModifierRepository modifiers;

	public CatalogService(CategoryRepository categories, ItemRepository items, VariantRepository variants,
			TaxCodeRepository taxes, ModifierRepository modifiers) {
		this.categories = categories;
		this.items = items;
		this.variants = variants;
		this.taxes = taxes;
		this.modifiers = modifiers;
	}

	@Transactional
	public Map<String, Object> createTax(String code, int rateBps) {
		TaxCodeEntity t = new TaxCodeEntity();
		t.setTenantId(TenantContext.require().tenantId());
		t.setCode(code);
		t.setRateBps(rateBps);
		taxes.save(t);
		return Map.of("id", t.getId(), "code", code, "rateBps", rateBps);
	}

	@Transactional
	public Map<String, Object> createCategory(UUID outletId, String name) {
		System.out.println("Creating category for outletId: " + outletId + " with name: " + name);
		CategoryEntity c = new CategoryEntity();
		c.setTenantId(TenantContext.require().tenantId());
		c.setOutletId(outletId);
		c.setName(name);
		categories.save(c);
		return Map.of("id", c.getId(), "name", name);
	}

	@Transactional
	public Map<String, Object> createItem(UUID outletId, UUID categoryId, String name, long pricePaise, UUID taxCodeId,
			boolean availableOnQr) {
		System.out.println("Creating item for outletId: " + outletId + " with name: " + name);
		ItemEntity i = new ItemEntity();
		i.setTenantId(TenantContext.require().tenantId());
		i.setOutletId(outletId);
		i.setCategoryId(categoryId);
		i.setName(name);
		i.setTaxCodeId(taxCodeId);
		i.setAvailableOnQr(availableOnQr);
		items.save(i);
		VariantEntity v = new VariantEntity();
		v.setTenantId(i.getId() == null ? TenantContext.require().tenantId() : TenantContext.require().tenantId());
		v.setItemId(i.getId());
		v.setName("Default");
		v.setPricePaise(pricePaise);
		variants.save(v);
		return Map.of("itemId", i.getId(), "variantId", v.getId(), "pricePaise", pricePaise);
	}

	@Transactional
	public Map<String, Object> createModifier(UUID outletId, String name, long extraPaise) {
		ModifierEntity m = new ModifierEntity();
		m.setTenantId(TenantContext.require().tenantId());
		m.setOutletId(outletId);
		m.setName(name);
		m.setExtraPaise(extraPaise);
		modifiers.save(m);
		return Map.of("id", m.getId(), "name", name, "extraPaise", extraPaise);
	}

	public List<Map<String, Object>> channelMenu(UUID outletId, boolean qr) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (ItemEntity i : items.findByTenantId(TenantContext.require().tenantId())) {
			if (i.isDeleted() || i.isEightySixed())
				continue;
			if (qr && !i.isAvailableOnQr())
				continue;
			for (VariantEntity v : variants.findByTenantId(TenantContext.require().tenantId())) {
				if (!v.getItemId().equals(i.getId()))
					continue;
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("itemId", i.getId());
				row.put("variantId", v.getId());
				row.put("name", i.getName());
				row.put("variant", v.getName());
				row.put("pricePaise", v.getPricePaise());
				out.add(row);
			}
		}
		return out;
	}

	public VariantEntity requireVariant(UUID id) {
		return variants.findById(id).orElseThrow(() -> ApiException.notFound("VARIANT", "Variant not found"));
	}

	public ItemEntity requireItem(UUID id) {
		return items.findById(id).orElseThrow(() -> ApiException.notFound("ITEM", "Item not found"));
	}

	public TaxCodeEntity tax(UUID id) {
		return id == null ? null : taxes.findById(id).orElse(null);
	}

	public ModifierEntity modifier(UUID id) {
		return modifiers.findById(id).orElseThrow(() -> ApiException.notFound("MODIFIER", "Modifier not found"));
	}
}
