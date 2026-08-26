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
import java.util.function.Function;
import java.util.stream.Collectors;

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
		requireStaffOutlet(outletId); name = text(name, "Category", 80, false);
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
		return createItem(outletId, categoryId, name, "", null, pricePaise, taxCodeId, availableOnQr, true);
	}

	@Transactional
	public Map<String, Object> createItem(UUID outletId, UUID categoryId, String name, String description, String imageUrl,
			long pricePaise, UUID taxCodeId, boolean availableOnQr, boolean availableOnCounter) {
		requireStaffOutlet(outletId); validateItem(categoryId, outletId, name, description, imageUrl, pricePaise);
		ItemEntity i = new ItemEntity();
		i.setTenantId(TenantContext.require().tenantId());
		i.setOutletId(outletId);
		i.setCategoryId(categoryId);
		i.setName(text(name, "Name", 120, false)); i.setDescription(text(description, "Description", 500, true)); i.setImageUrl(image(imageUrl));
		i.setTaxCodeId(taxCodeId);
		i.setAvailableOnQr(availableOnQr);
		i.setAvailableOnCounter(availableOnCounter);
		i.setEightySixed(!availableOnCounter);
		items.save(i);
		VariantEntity v = new VariantEntity();
		v.setTenantId(i.getId() == null ? TenantContext.require().tenantId() : TenantContext.require().tenantId());
		v.setItemId(i.getId());
		v.setName("Default");
		v.setPricePaise(pricePaise);
		variants.save(v);
		return itemView(i, v);
	}

	@Transactional
	public Map<String, Object> updateItem(UUID itemId, UUID categoryId, String name, String description, String imageUrl,
			long pricePaise, boolean availableOnQr, boolean availableOnCounter) {
		ItemEntity item = requireActiveItem(itemId); requireStaffOutlet(item.getOutletId());
		validateItem(categoryId, item.getOutletId(), name, description, imageUrl, pricePaise);
		VariantEntity variant = defaultVariant(itemId);
		item.setCategoryId(categoryId); item.setName(text(name, "Name", 120, false)); item.setDescription(text(description, "Description", 500, true));
		item.setImageUrl(image(imageUrl)); item.setAvailableOnQr(availableOnQr); item.setAvailableOnCounter(availableOnCounter); item.setEightySixed(!availableOnCounter);
		variant.setPricePaise(pricePaise); items.save(item); variants.save(variant); return itemView(item, variant);
	}

	@Transactional
	public Map<String, Object> setAvailability(UUID itemId, boolean available) {
		ItemEntity item = requireActiveItem(itemId); requireStaffOutlet(item.getOutletId());
		item.setAvailableOnCounter(available); item.setAvailableOnQr(available); item.setEightySixed(!available); items.save(item);
		return itemView(item, defaultVariant(itemId));
	}

	@Transactional
	public void deleteItem(UUID itemId) {
		ItemEntity item = requireActiveItem(itemId); requireStaffOutlet(item.getOutletId());
		item.setDeleted(true); item.setAvailableOnCounter(false); item.setAvailableOnQr(false); item.setEightySixed(true); items.save(item);
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
		requireReadableOutlet(outletId);
		List<ItemEntity> menuItems = items.findByOutletIdAndDeletedFalse(outletId);
		Map<UUID, List<VariantEntity>> variantsByItem = menuItems.isEmpty() ? Map.of()
				: variants.findByItemIdIn(menuItems.stream().map(ItemEntity::getId).toList()).stream()
						.collect(Collectors.groupingBy(VariantEntity::getItemId));
		Map<UUID, CategoryEntity> categoriesById = categories.findAllById(menuItems.stream()
				.map(ItemEntity::getCategoryId).distinct().toList()).stream()
				.collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));
		List<Map<String, Object>> out = new ArrayList<>();
		for (ItemEntity i : menuItems) {
			if (i.isDeleted() || (qr && i.isEightySixed()))
				continue;
			if (qr && !i.isAvailableOnQr())
				continue;
			for (VariantEntity v : variantsByItem.getOrDefault(i.getId(), List.of())) {
				if (!v.getItemId().equals(i.getId()))
					continue;
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("itemId", i.getId());
				row.put("variantId", v.getId());
				row.put("name", i.getName());
				row.put("variant", v.getName());
				row.put("pricePaise", v.getPricePaise());
				row.put("description", i.getDescription()); row.put("image", i.getImageUrl());
				CategoryEntity category = categoriesById.get(i.getCategoryId());
				row.put("categoryId", i.getCategoryId()); row.put("category", category == null ? "Uncategorized" : category.getName());
				row.put("available", !i.isEightySixed() && i.isAvailableOnCounter());
				out.add(row);
			}
		}
		return out;
	}

	public List<Map<String, Object>> listCategories(UUID outletId) {
		requireStaffOutlet(outletId);
		return categories.findByOutletIdAndDeletedFalseOrderBySortOrderAscNameAsc(outletId).stream()
				.map(category -> Map.<String, Object>of("id", category.getId(), "name", category.getName())).toList();
	}

	public VariantEntity requireVariant(UUID id) {
		return variants.findById(id).orElseThrow(() -> ApiException.notFound("VARIANT", "Variant not found"));
	}

	public ItemEntity requireItem(UUID id) {
		return items.findById(id).orElseThrow(() -> ApiException.notFound("ITEM", "Item not found"));
	}

	private ItemEntity requireActiveItem(UUID id) { ItemEntity item = requireItem(id); if (item.isDeleted()) throw ApiException.notFound("ITEM", "Item not found"); return item; }
	private VariantEntity defaultVariant(UUID itemId) { return variants.findByItemId(itemId).stream().findFirst().orElseThrow(() -> ApiException.notFound("VARIANT", "Item has no variant")); }
	private Map<String, Object> itemView(ItemEntity item, VariantEntity variant) {
		Map<String, Object> row = new LinkedHashMap<>(); CategoryEntity category = categories.findById(item.getCategoryId()).orElse(null);
		row.put("itemId", item.getId()); row.put("variantId", variant.getId()); row.put("name", item.getName()); row.put("description", item.getDescription());
		row.put("image", item.getImageUrl()); row.put("categoryId", item.getCategoryId()); row.put("category", category == null ? "Uncategorized" : category.getName());
		row.put("pricePaise", variant.getPricePaise()); row.put("available", !item.isEightySixed() && item.isAvailableOnCounter()); return row;
	}
	private void validateItem(UUID categoryId, UUID outletId, String name, String description, String imageUrl, long pricePaise) {
		text(name, "Name", 120, false); text(description, "Description", 500, true); image(imageUrl);
		if (pricePaise <= 0 || pricePaise > 100_000_000L) throw ApiException.bad("VALIDATION", "Price must be between 1 and 100000000 paise");
		CategoryEntity category = categories.findById(categoryId).orElseThrow(() -> ApiException.notFound("CATEGORY", "Category not found"));
		if (category.isDeleted() || !outletId.equals(category.getOutletId())) throw ApiException.bad("CATEGORY_OUTLET", "Category does not belong to this outlet");
	}
	private static String text(String value, String field, int max, boolean optional) { if (value == null) value = ""; value = value.trim(); if (!optional && value.isEmpty()) throw ApiException.bad("VALIDATION", field + " is required"); if (value.length() > max) throw ApiException.bad("VALIDATION", field + " must be " + max + " characters or fewer"); return value; }
	private static String image(String value) { if (value == null || value.isBlank()) return null; value = value.trim(); boolean uploaded=value.matches("/api/v1/public/menu-images/[0-9a-fA-F-]{36}"); if (value.length() > 2048 || (!(value.startsWith("https://") || value.startsWith("http://")) && !uploaded)) throw ApiException.bad("VALIDATION", "Image must be an uploaded menu image or a valid HTTP(S) URL"); return value; }
	private static void requireStaffOutlet(UUID outletId) { var p = TenantContext.require(); if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Guests cannot manage the menu"); if (p.outletIds() == null || !p.outletIds().contains(outletId)) throw ApiException.forbidden("OUTLET_ACCESS", "You do not have access to this outlet"); }
	private static void requireReadableOutlet(UUID outletId) { var p = TenantContext.require(); if (p.isGuest()) { if (!outletId.equals(p.outletId())) throw ApiException.forbidden("OUTLET_ACCESS", "Wrong outlet"); } else if (p.outletIds() == null || !p.outletIds().contains(outletId)) throw ApiException.forbidden("OUTLET_ACCESS", "You do not have access to this outlet"); }

	public TaxCodeEntity tax(UUID id) {
		return id == null ? null : taxes.findById(id).orElse(null);
	}

	public ModifierEntity modifier(UUID id) {
		return modifiers.findById(id).orElseThrow(() -> ApiException.notFound("MODIFIER", "Modifier not found"));
	}
}
