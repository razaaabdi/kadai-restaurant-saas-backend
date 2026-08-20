package com.restaurant.order.application;

import com.restaurant.billing.api.BillingFacade;
import com.restaurant.catalog.application.CatalogService;
import com.restaurant.catalog.infrastructure.ItemEntity;
import com.restaurant.catalog.infrastructure.TaxCodeEntity;
import com.restaurant.catalog.infrastructure.VariantEntity;
import com.restaurant.inventory.api.InventoryFacade;
import com.restaurant.order.domain.OrderStatus;
import com.restaurant.order.infrastructure.OrderEntity;
import com.restaurant.order.infrastructure.OrderLineEntity;
import com.restaurant.order.infrastructure.OrderLineModifierEntity;
import com.restaurant.order.infrastructure.OrderLineModifierRepository;
import com.restaurant.order.infrastructure.OrderLineRepository;
import com.restaurant.order.infrastructure.OrderRepository;
import com.restaurant.order.infrastructure.OrderRoundEntity;
import com.restaurant.order.infrastructure.OrderRoundRepository;
import com.restaurant.outlet.api.QrLookup;
import com.restaurant.outlet.application.FloorService;
import com.restaurant.outlet.infrastructure.OutletEntity;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.InvoicePaid;
import com.restaurant.platform.api.KotStatusChanged;
import com.restaurant.platform.api.Money;
import com.restaurant.platform.api.Quantity;
import com.restaurant.platform.api.RoundConfirmed;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.TenantPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {
	private final OrderRepository orders;
	private final OrderRoundRepository rounds;
	private final OrderLineRepository lines;
	private final OrderLineModifierRepository lineMods;
	private final CatalogService catalog;
	private final InventoryFacade inventory;
	private final FloorService floor;
	private final BillingFacade billing;
	private final ApplicationEventPublisher events;

	public OrderService(OrderRepository orders, OrderRoundRepository rounds, OrderLineRepository lines,
			OrderLineModifierRepository lineMods, CatalogService catalog, InventoryFacade inventory,
			FloorService floor, BillingFacade billing, ApplicationEventPublisher events) {
		this.orders = orders;
		this.rounds = rounds;
		this.lines = lines;
		this.lineMods = lineMods;
		this.catalog = catalog;
		this.inventory = inventory;
		this.floor = floor;
		this.billing = billing;
		this.events = events;
	}

	@Transactional
	public Map<String, Object> guestRound(String token, List<Map<String, Object>> items) {
		TenantPrincipal guest = TenantContext.require();
		if (!guest.isGuest()) throw ApiException.forbidden("GUEST_ONLY", "Use staff order API");
		floor.assertGuestTokenLive(guest);
		QrLookup q = floor.requireToken(token);
		if (!q.tableId().equals(guest.tableId())) {
			throw ApiException.forbidden("WRONG_TABLE", "Token is not this table");
		}
		if (q.qrLocked()) throw ApiException.gone("QR_LOCKED", "QR locked");
		return addRoundInternal(q.outletId(), q.tableId(), "QR_DINE_IN", items, true);
	}

	@Transactional
	public Map<String, Object> counterOrder(UUID outletId, UUID tableId, String channel, List<Map<String, Object>> items) {
		TenantPrincipal p = TenantContext.require();
		if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Staff counter path");
		if (channel == null) channel = tableId == null ? "TAKEAWAY" : "COUNTER_DINE_IN";
		return addRoundInternal(outletId, tableId, channel, items, true);
	}

	@Transactional
	public Map<String, Object> addStaffRound(UUID orderId, List<Map<String, Object>> items) {
		TenantPrincipal p = TenantContext.require(); if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Guests cannot use the staff order API");
		OrderEntity order = requireOwn(orderId);
		if (!OrderStatus.open(order.getStatus())) throw ApiException.conflict("ORDER_CLOSED", "This order is already closed");
		return addRoundInternal(order.getOutletId(), order.getTableId(), order.getChannel(), items, true);
	}

	public Map<String, Object> activeForTable(UUID outletId, UUID tableId) {
		TenantPrincipal p = TenantContext.require(); if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Guests cannot use the staff order API");
		OrderEntity order = openForTable(outletId, tableId);
		if (order == null) throw ApiException.notFound("ORDER", "No active order for this table"); return toView(order);
	}

	@Transactional
	public Map<String, Object> requestBill(UUID orderId, boolean generate, long discountPaise) {
		OrderEntity o = requireOwn(orderId);
		TenantPrincipal p = TenantContext.require();
		if (p.isGuest()) {
			OutletEntity out = floor.outlet(o.getOutletId());
			if (!out.isQrGuestCanRequestBill()) throw ApiException.forbidden("BILL", "Guest cannot request bill");
			discountPaise = 0;
		} else if (discountPaise > 0 && !(p.hasRole("OWNER") || p.hasRole("MANAGER"))) {
			throw ApiException.forbidden("DISCOUNT", "No discount permission");
		}
		if (OrderStatus.BILL_REQUESTED.equals(o.getStatus()) && generate) {
			return invoiceFrom(o, discountPaise);
		}
		OrderStatus.assertTransition(o.getStatus(), OrderStatus.BILL_REQUESTED);
		o.setStatus(OrderStatus.BILL_REQUESTED);
		o.setGuestFrozen(true);
		orders.save(o);
		if (o.getTableId() != null) floor.markBillRequested(o.getTableId());
		if (generate || !p.isGuest()) {
			return invoiceFrom(o, discountPaise);
		}
		return toView(o);
	}

	@Transactional
	public Map<String, Object> unlockAdd(UUID orderId) {
		TenantPrincipal p = TenantContext.require();
		if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Waiter only");
		OrderEntity o = requireOwn(orderId);
		if (!floor.outlet(o.getOutletId()).isUnlockAddBeforeBill()) {
			throw ApiException.conflict("LOCKED", "Unlock disabled");
		}
		if (!OrderStatus.BILL_REQUESTED.equals(o.getStatus())) {
			throw ApiException.conflict("ORDER_ILLEGAL_STATUS", "Not bill requested");
		}
		o.setStatus(OrderStatus.READY);
		o.setGuestFrozen(false);
		orders.save(o);
		return toView(o);
	}

	@Transactional
	public Map<String, Object> cancel(UUID orderId) {
		OrderEntity o = requireOwn(orderId);
		TenantPrincipal p = TenantContext.require();
		if (p.isGuest()) throw ApiException.forbidden("GUEST_VOID", "Guest cannot void");
		String to = OrderStatus.CANCELLED;
		OrderStatus.assertTransition(o.getStatus(), to);
		boolean afterKot = List.of(OrderStatus.KOT_SENT, OrderStatus.PREPARING).contains(o.getStatus());
		o.setStatus(to);
		orders.save(o);
		if (afterKot) inventory.reverseVoid(o.getOutletId(), o.getId());
		if (o.getTableId() != null) floor.clearTable(o.getTableId());
		return toView(o);
	}

	@Transactional
	public Map<String, Object> illegalPatch(UUID orderId, String status) {
		OrderEntity o = requireOwn(orderId);
		OrderStatus.assertTransition(o.getStatus(), status);
		o.setStatus(status);
		orders.save(o);
		return toView(o);
	}

	public Map<String, Object> get(UUID orderId) {
		return toView(requireOwn(orderId));
	}

	public Map<String, Object> tableOrder(String token) {
		TenantPrincipal guest = TenantContext.require();
		floor.assertGuestTokenLive(guest);
		QrLookup q = floor.requireToken(token);
		if (!q.tableId().equals(guest.tableId())) throw ApiException.forbidden("WRONG_TABLE", "Wrong table");
		OrderEntity o = openForTable(q.outletId(), q.tableId());
		if (o == null) throw ApiException.notFound("ORDER", "No open order");
		return toView(o);
	}

	@EventListener
	@Transactional
	public void onKot(KotStatusChanged ev) {
		orders.findById(ev.orderId()).ifPresent(o -> {
			if ("PREPARING".equals(ev.kotStatus()) && OrderStatus.KOT_SENT.equals(o.getStatus())) {
				OrderStatus.assertTransition(o.getStatus(), OrderStatus.PREPARING);
				o.setStatus(OrderStatus.PREPARING);
				orders.save(o);
			}
			if ("READY".equals(ev.kotStatus()) && List.of(OrderStatus.KOT_SENT, OrderStatus.PREPARING).contains(o.getStatus())) {
				o.setStatus(OrderStatus.READY);
				orders.save(o);
			}
		});
	}

	@EventListener
	@Transactional
	public void onPaid(InvoicePaid ev) {
		OrderEntity o = orders.findById(ev.orderId()).orElseThrow();
		if (OrderStatus.BILLED.equals(o.getStatus())) {
			OrderStatus.assertTransition(o.getStatus(), OrderStatus.PAID);
			o.setStatus(OrderStatus.PAID);
			orders.save(o);
			OrderStatus.assertTransition(o.getStatus(), OrderStatus.COMPLETED);
			o.setStatus(OrderStatus.COMPLETED);
			orders.save(o);
			if (o.getTableId() != null) floor.markPaidDirty(o.getTableId());
		}
	}

	private Map<String, Object> addRoundInternal(UUID outletId, UUID tableId, String channel, List<Map<String, Object>> items,
			boolean autoConfirm) {
		TenantPrincipal p = TenantContext.require();
		if (items == null || items.isEmpty()) throw ApiException.bad("EMPTY_ORDER", "Add at least one item");
		if (items.size() > 100) throw ApiException.bad("TOO_MANY_ITEMS", "An order round can contain at most 100 items");
		if (tableId != null) {
			var table = floor.lockForOrder(tableId);
			if (!outletId.equals(table.getOutletId())) throw ApiException.bad("TABLE_OUTLET", "Table does not belong to this outlet");
		}
		OrderEntity o = tableId == null ? null : openForTable(outletId, tableId);
		boolean created = false;
		if (o == null) {
			o = new OrderEntity();
			o.setTenantId(p.tenantId());
			o.setOutletId(outletId);
			o.setTableId(tableId);
			o.setChannel(channel);
			o.setStatus(OrderStatus.DRAFT);
			orders.save(o);
			created = true;
		} else if (o.isGuestFrozen()) {
			throw ApiException.conflict("FROZEN", "Bill requested; cannot add");
		}
		int nextNo = rounds.findByOrderIdOrderByRoundNo(o.getId()).size() + 1;
		OrderRoundEntity round = new OrderRoundEntity();
		round.setTenantId(p.tenantId());
		round.setOrderId(o.getId());
		round.setRoundNo(nextNo);
		rounds.save(round);

		OutletEntity outlet = floor.outlet(outletId);
		long added = 0;
		List<UUID> recipeIds = new ArrayList<>();
		for (Map<String, Object> it : items) {
			UUID variantId = UUID.fromString(String.valueOf(it.get("variantId")));
			BigDecimal qty = new Quantity(new BigDecimal(String.valueOf(it.getOrDefault("qty", "1")))).value();
			VariantEntity v = catalog.requireVariant(variantId);
			ItemEntity item = catalog.requireItem(v.getItemId());
			if (item.isEightySixed()) throw ApiException.conflict("86", "Item not available");
			if ("QR_DINE_IN".equals(channel) && !item.isAvailableOnQr()) {
				throw ApiException.conflict("NOT_ON_QR", "Item hidden from QR");
			}
			long extra = 0;
			OrderLineEntity line = new OrderLineEntity();
			line.setTenantId(p.tenantId());
			line.setOrderId(o.getId());
			line.setRoundId(round.getId());
			line.setVariantId(variantId);
			line.setNameSnapshot(item.getName() + " / " + v.getName());
			line.setQty(qty);
			line.setUnitPaise(v.getPricePaise());
			UUID recipe = inventory.latestRecipe(variantId);
			line.setRecipeVersionId(recipe);
			if (it.get("modifierIds") instanceof List<?> mods) {
				for (Object mid : mods) {
					var m = catalog.modifier(UUID.fromString(String.valueOf(mid)));
					extra += m.getExtraPaise();
				}
			}
			long linePaise = new Money(v.getPricePaise() + extra).times(qty).paise();
			line.setLinePaise(linePaise);
			lines.save(line);
			added += linePaise;
			if (it.get("modifierIds") instanceof List<?> mods) {
				for (Object mid : mods) {
					var m = catalog.modifier(UUID.fromString(String.valueOf(mid)));
					OrderLineModifierEntity lm = new OrderLineModifierEntity();
					lm.setTenantId(p.tenantId());
					lm.setOrderLineId(line.getId());
					lm.setModifierId(m.getId());
					lm.setNameSnapshot(m.getName());
					lm.setExtraPaise(m.getExtraPaise());
					lineMods.save(lm);
				}
			}
			if (recipe != null) recipeIds.add(recipe);
		}
		o.setSubtotalPaise(o.getSubtotalPaise() + added);
		if (o.getSubtotalPaise() > outlet.getMaxOpenAmountPaise()) {
			throw ApiException.conflict("MAX_OPEN", "Over table open amount cap");
		}
		orders.save(o);
		if (autoConfirm && (created || !OrderStatus.DRAFT.equals(o.getStatus()) || outlet.isQrAutoConfirm() || !p.isGuest())) {
			confirmRound(o, round, outlet);
		}
		return toView(o);
	}

	private void confirmRound(OrderEntity o, OrderRoundEntity round, OutletEntity outlet) {
		if (OrderStatus.DRAFT.equals(o.getStatus())) {
			OrderStatus.assertTransition(o.getStatus(), OrderStatus.CONFIRMED);
			o.setStatus(OrderStatus.CONFIRMED);
		}
		boolean allowNeg = outlet.isAllowNegativeStock();
		for (OrderLineEntity line : lines.findByOrderId(o.getId())) {
			if (!line.getRoundId().equals(round.getId())) continue;
			inventory.deductSale(o.getOutletId(), o.getId(), line.getRecipeVersionId(), line.getQty(), allowNeg);
		}
		if (OrderStatus.CONFIRMED.equals(o.getStatus())) {
			OrderStatus.assertTransition(o.getStatus(), OrderStatus.KOT_SENT);
			o.setStatus(OrderStatus.KOT_SENT);
		}
		orders.save(o);
		if (o.getTableId() != null) floor.occupy(o.getTableId());
		events.publishEvent(new RoundConfirmed(o.getTenantId(), o.getOutletId(), o.getId(), round.getId()));
	}

	private Map<String, Object> invoiceFrom(OrderEntity o, long discountPaise) {
		int taxBps = 0;
		List<Map<String, Object>> snaps = new ArrayList<>();
		for (OrderLineEntity l : lines.findByOrderId(o.getId())) {
			snaps.add(Map.of("name", l.getNameSnapshot(), "qty", l.getQty().toPlainString(),
					"unitPaise", l.getUnitPaise(), "linePaise", l.getLinePaise()));
			VariantEntity v = catalog.requireVariant(l.getVariantId());
			ItemEntity item = catalog.requireItem(v.getItemId());
			TaxCodeEntity tax = catalog.tax(item.getTaxCodeId());
			if (tax != null) taxBps = tax.getRateBps();
		}
		OutletEntity out = floor.outlet(o.getOutletId());
		var inv = billing.generate(o.getOutletId(), o.getId(), o.getChannel(), o.getSubtotalPaise(), discountPaise,
				out.getServiceChargeBps(), out.getPackagingChargePaise(), out.isTaxInclusive(), taxBps, snaps);
		OrderStatus.assertTransition(o.getStatus(), OrderStatus.BILLED);
		o.setStatus(OrderStatus.BILLED);
		o.setDiscountPaise(discountPaise);
		o.setTotalPaise(inv.getTotalPaise());
		o.setTaxPaise(inv.getTaxPaise());
		orders.save(o);
		Map<String, Object> view = new LinkedHashMap<>(toView(o));
		view.put("invoiceId", inv.getId());
		view.put("invoiceTotalPaise", inv.getTotalPaise());
		return view;
	}

	private OrderEntity openForTable(UUID outletId, UUID tableId) {
		return orders.findByOutletIdAndTableId(outletId, tableId).stream()
				.filter(x -> OrderStatus.open(x.getStatus()))
				.findFirst().orElse(null);
	}

	private OrderEntity requireOwn(UUID orderId) {
		TenantPrincipal p = TenantContext.require();
		OrderEntity o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("ORDER", "Order not found"));
		if (p.isGuest() && (o.getTableId() == null || !o.getTableId().equals(p.tableId()))) {
			throw ApiException.forbidden("WRONG_TABLE", "Not your table");
		}
		return o;
	}

	private Map<String, Object> toView(OrderEntity o) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", o.getId());
		m.put("status", o.getStatus());
		m.put("channel", o.getChannel());
		m.put("tableId", o.getTableId());
		m.put("outletId", o.getOutletId());
		m.put("subtotalPaise", o.getSubtotalPaise());
		m.put("totalPaise", o.getTotalPaise());
		m.put("guestFrozen", o.isGuestFrozen());
		m.put("rounds", rounds.findByOrderIdOrderByRoundNo(o.getId()).size());
		return m;
	}
}
