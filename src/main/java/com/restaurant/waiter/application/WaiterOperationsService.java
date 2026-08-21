package com.restaurant.waiter.application;

import com.restaurant.billing.infrastructure.InvoiceEntity;
import com.restaurant.billing.infrastructure.InvoiceRepository;
import com.restaurant.kitchen.infrastructure.KotEntity;
import com.restaurant.kitchen.infrastructure.KotRepository;
import com.restaurant.order.application.OrderService;
import com.restaurant.order.domain.OrderStatus;
import com.restaurant.order.infrastructure.OrderEntity;
import com.restaurant.order.infrastructure.OrderLineEntity;
import com.restaurant.order.infrastructure.OrderLineRepository;
import com.restaurant.order.infrastructure.OrderRepository;
import com.restaurant.payment.infrastructure.PaymentEntity;
import com.restaurant.payment.infrastructure.PaymentRepository;
import com.restaurant.outlet.infrastructure.DiningTableRepository;
import com.restaurant.outlet.infrastructure.TableEntity;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.AuditWriter;
import com.restaurant.platform.api.KotStatusChanged;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.waiter.infrastructure.WaiterNotificationEntity;
import com.restaurant.waiter.infrastructure.WaiterNotificationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WaiterOperationsService {
	private final OrderRepository orders;
	private final OrderLineRepository lines;
	private final KotRepository kots;
	private final DiningTableRepository tables;
	private final InvoiceRepository invoices;
	private final WaiterNotificationRepository notifications;
	private final AuditWriter audit;
	private final OrderService orderService;
	private final JdbcTemplate jdbc;
	private final PaymentRepository payments;

	public WaiterOperationsService(OrderRepository orders, OrderLineRepository lines, KotRepository kots,
			DiningTableRepository tables, InvoiceRepository invoices, WaiterNotificationRepository notifications,
			AuditWriter audit, OrderService orderService, JdbcTemplate jdbc,PaymentRepository payments) {
		this.orders = orders; this.lines = lines; this.kots = kots; this.tables = tables; this.invoices = invoices;
		this.notifications = notifications; this.audit = audit; this.orderService = orderService; this.jdbc = jdbc;this.payments=payments;
	}

	public List<Map<String, Object>> activeOrders(UUID outletId, boolean mineOnly) {
		requireOutlet(outletId);
		UUID userId = TenantContext.require().userId();
		return orders.findByOutletIdOrderByCreatedAtDesc(outletId).stream()
				.filter(order -> order.getTableId() != null && (OrderStatus.open(order.getStatus()) || tables.findById(order.getTableId()).map(table -> Set.of("CLEANING_REQUIRED", "CLEANING").contains(table.getStatus())).orElse(false)))
				.filter(order -> !mineOnly || userId == null || userId.equals(order.getAssignedWaiterId()))
				.map(this::summary).toList();
	}

	public Map<String, Object> detail(UUID outletId, UUID orderId) {
		requireOutlet(outletId); OrderEntity order = requireOrder(orderId, outletId);
		Map<String, Object> view = new LinkedHashMap<>(summary(order));
		view.put("items", lines.findByOrderId(orderId).stream().map(this::lineView).toList());
		view.put("kots", kots.findByOrderId(orderId).stream().map(this::kotView).toList());
		view.put("timeline", timeline(order));
		return view;
	}

	@Transactional
	public Map<String, Object> pickup(UUID outletId, UUID orderId, UUID itemId) {
		OrderEntity order = requireOrder(orderId, outletId); OrderLineEntity line = requireLine(orderId, itemId);
		requireServiceOwner(order);
		if (Set.of("PICKED_UP", "SERVED").contains(line.getFulfilmentStatus())) return detail(outletId, orderId);
		if (!"READY_FOR_PICKUP".equals(line.getFulfilmentStatus())) throw ApiException.conflict("ITEM_TRANSITION", "Only a ready item can be picked up");
		line.setFulfilmentStatus("PICKED_UP"); line.setPickedUpBy(TenantContext.require().userId()); line.setPickedUpAt(Instant.now()); lines.save(line);
		recalculateKot(line.getRoundId()); audit.write("ORDER_ITEM_PICKED_UP", "ORDER_LINE", itemId, "order=" + order.getId());
		return detail(outletId, orderId);
	}

	@Transactional
	public Map<String, Object> serve(UUID outletId, UUID orderId, UUID itemId) {
		OrderEntity order = requireOrder(orderId, outletId); OrderLineEntity line = requireLine(orderId, itemId);
		requireServiceOwner(order);
		if ("SERVED".equals(line.getFulfilmentStatus())) return detail(outletId, orderId);
		if (!"PICKED_UP".equals(line.getFulfilmentStatus())) throw ApiException.conflict("ITEM_TRANSITION", "Only a picked-up item can be served");
		line.setFulfilmentStatus("SERVED"); line.setServedBy(TenantContext.require().userId()); line.setServedAt(Instant.now()); lines.save(line);
		recalculateKot(line.getRoundId()); audit.write("ORDER_ITEM_SERVED", "ORDER_LINE", itemId, "order=" + order.getId());
		return detail(outletId, orderId);
	}

	@Transactional
	public Map<String,Object> bulkTransition(UUID outletId,UUID orderId,String action,String expectedSourceStatus,List<UUID> itemIds){
		OrderEntity order=requireOrder(orderId,outletId);requireServiceOwner(order);
		if(itemIds==null||itemIds.isEmpty())throw ApiException.bad("EMPTY_BULK_ACTION","Select at least one order item");
		if(itemIds.size()>100)throw ApiException.bad("BULK_LIMIT","At most 100 items can be updated together");
		if(itemIds.stream().distinct().count()!=itemIds.size())throw ApiException.bad("DUPLICATE_ITEMS","Each order item may appear only once");
		String normalized=action==null?"":action.toUpperCase();String source;String target;
		if("PICK_UP".equals(normalized)){source="READY_FOR_PICKUP";target="PICKED_UP";}
		else if("MARK_SERVED".equals(normalized)){source="PICKED_UP";target="SERVED";}
		else throw ApiException.bad("BULK_ACTION","Waiter bulk action must be PICK_UP or MARK_SERVED");
		if(expectedSourceStatus==null||!source.equals(expectedSourceStatus.toUpperCase()))throw ApiException.conflict("STALE_BULK_ACTION","The expected source status no longer matches this action");
		List<OrderLineEntity> selected=itemIds.stream().map(id->requireLine(orderId,id)).toList();
		if(selected.stream().anyMatch(line->!source.equals(line.getFulfilmentStatus())))throw ApiException.conflict("ITEM_TRANSITION","One or more selected items changed status; refresh and try again");
		UUID actor=TenantContext.require().userId();Instant now=Instant.now();
		for(OrderLineEntity line:selected){line.setFulfilmentStatus(target);if("PICKED_UP".equals(target)){line.setPickedUpBy(actor);line.setPickedUpAt(now);}else{line.setServedBy(actor);line.setServedAt(now);}lines.save(line);}
		selected.stream().map(OrderLineEntity::getRoundId).distinct().forEach(this::recalculateKot);
		audit.write("ORDER_ITEMS_"+target,"ORDER",orderId,"count="+selected.size());
		Map<String,Object> result=new LinkedHashMap<>(detail(outletId,orderId));result.put("updatedItemIds",itemIds);result.put("updatedCount",selected.size());return result;
	}

	@Transactional
	public Map<String, Object> requestBill(UUID outletId, UUID orderId) {
		OrderEntity order = requireOrder(orderId, outletId); requireServiceOwner(order);
		if (lines.findByOrderId(orderId).stream().anyMatch(line -> !Set.of("SERVED", "CANCELLED").contains(line.getFulfilmentStatus())))
			throw ApiException.conflict("SERVICE_INCOMPLETE", "All active items must be served before requesting the bill");
		Map<String, Object> result = orderService.requestBill(orderId, false, 0);
		audit.write("BILL_REQUESTED", "ORDER", orderId, "Requested from waiter operations"); return result;
	}

	@Transactional
	public Map<String, Object> generateInvoice(UUID outletId, UUID orderId, long discountPaise) {
		requireOrder(orderId, outletId);
		Map<String, Object> result = orderService.requestBill(orderId, true, discountPaise);
		audit.write("INVOICE_GENERATED", "ORDER", orderId, "discountPaise=" + discountPaise); return result;
	}

	public List<Map<String, Object>> notifications(UUID outletId) {
		requireOutlet(outletId); var principal = TenantContext.require();
		return notifications.findByOutletIdOrderByCreatedAtDesc(outletId).stream()
				.filter(n -> principal.hasRole("OWNER") || principal.hasRole("MANAGER") || principal.userId() == null || principal.userId().equals(n.getRecipientUserId()))
				.map(this::notificationView).toList();
	}

	@Transactional
	public void acknowledge(UUID outletId, UUID notificationId) {
		requireOutlet(outletId);
		WaiterNotificationEntity notification = notifications.findById(notificationId).orElseThrow(() -> ApiException.notFound("NOTIFICATION", "Notification not found"));
		var principal = TenantContext.require();
		if (!outletId.equals(notification.getOutletId()) || (!(principal.hasRole("OWNER") || principal.hasRole("MANAGER")) && principal.userId() != null && !principal.userId().equals(notification.getRecipientUserId()))) throw ApiException.forbidden("NOTIFICATION_ACCESS", "You cannot acknowledge this notification");
		if (!notification.isAcknowledged()) { notification.setAcknowledged(true); notification.setAcknowledgedAt(Instant.now()); notifications.save(notification); }
	}

	@Transactional
	public Map<String,Object> acknowledgeAll(UUID outletId){
		requireOutlet(outletId);var principal=TenantContext.require();Instant now=Instant.now();int cleared=0;
		for(WaiterNotificationEntity notification:notifications.findByOutletIdOrderByCreatedAtDesc(outletId)){
			boolean allowed=principal.hasRole("OWNER")||principal.hasRole("MANAGER")||principal.userId()==null||principal.userId().equals(notification.getRecipientUserId());
			if(allowed&&!notification.isAcknowledged()){notification.setAcknowledged(true);notification.setAcknowledgedAt(now);notifications.save(notification);cleared++;}
		}
		audit.write("WAITER_NOTIFICATIONS_CLEARED","OUTLET",outletId,"count="+cleared);return Map.of("clearedCount",cleared);
	}

	@EventListener
	@Transactional
	public void onKotStatus(KotStatusChanged event) {
		if (!("READY".equals(event.kotStatus()) || "PARTIALLY_READY".equals(event.kotStatus()))) return;
		orders.findById(event.orderId()).ifPresent(order -> {
			String dedupe = "KOT_READY:" + event.kotId() + ":" + event.kotStatus();
			if (notifications.existsByDedupeKey(dedupe)) return;
			WaiterNotificationEntity notification = new WaiterNotificationEntity();
			notification.setTenantId(event.tenantId()); notification.setOutletId(order.getOutletId()); notification.setRecipientUserId(order.getAssignedWaiterId());
			notification.setEventType("PARTIALLY_READY".equals(event.kotStatus()) ? "ITEM_READY_FOR_PICKUP" : "KOT_READY_FOR_PICKUP");
			notification.setOrderId(order.getId()); notification.setTableId(order.getTableId()); notification.setKotId(event.kotId());
			notification.setRelatedItemIds(lines.findByRoundId(event.roundId()).stream().filter(line -> "READY_FOR_PICKUP".equals(line.getFulfilmentStatus())).map(line -> line.getId().toString()).reduce((a,b) -> a + "," + b).orElse(""));
			notification.setMessage("Kitchen items are ready for pickup"); notification.setDestination("/waiter/orders/" + order.getId()); notification.setDedupeKey(dedupe); notifications.save(notification);
		});
	}

	private Map<String, Object> summary(OrderEntity order) {
		TableEntity table = tables.findById(order.getTableId()).orElse(null); List<OrderLineEntity> orderLines = lines.findByOrderId(order.getId()); List<KotEntity> orderKots = kots.findByOrderId(order.getId());
		Map<String, Object> view = new LinkedHashMap<>(); view.put("orderId", order.getId()); view.put("orderNumber", order.getId().toString().substring(0, 8).toUpperCase());
		view.put("tableId", order.getTableId()); view.put("tableCode", table == null ? "—" : table.getCode()); view.put("tableStatus", table == null ? "UNKNOWN" : table.getStatus());
		UUID validWaiter=validWaiter(order.getOutletId(),order.getAssignedWaiterId())?order.getAssignedWaiterId():null;
		view.put("assignedWaiterId",validWaiter); view.put("guestCount", order.getGuestCount()); view.put("startedAt", order.getCreatedAt()); view.put("orderStatus", order.getStatus());
		view.put("assignedWaiterName",validWaiter==null?null:waiterName(validWaiter));
		view.put("itemCount", orderLines.stream().mapToInt(line -> line.getQty().intValue()).sum()); view.put("kotCount", orderKots.size());
		view.put("readyCount", count(orderLines, "READY_FOR_PICKUP")); view.put("pickedUpCount", count(orderLines, "PICKED_UP")); view.put("servedCount", count(orderLines, "SERVED"));
		String service=aggregate(orderLines);view.put("kitchenProgress",service);view.put("serviceStatus",service);InvoiceEntity invoice = invoices.findByOrderId(order.getId()).orElse(null);
		long paid=invoice==null?0:payments.findByInvoiceId(invoice.getId()).stream().filter(p->"SUCCESS".equals(p.getStatus())).mapToLong(PaymentEntity::getAmountPaise).sum();
		long total=invoice==null?order.getSubtotalPaise():invoice.getTotalPaise();long due=Math.max(0,total-paid);
		view.put("invoiceStatus",invoice==null?"NOT_GENERATED":"VOID".equals(invoice.getStatus())?"VOID":"GENERATED");
		view.put("paymentStatus",invoice==null?"NOT_STARTED":due==0?"PAID":paid>0?"PARTIALLY_PAID":"AWAITING_PAYMENT");
		view.put("subtotalPaise",invoice==null?order.getSubtotalPaise():invoice.getSubtotalPaise());view.put("taxPaise",invoice==null?order.getTaxPaise():invoice.getTaxPaise());view.put("discountPaise",invoice==null?order.getDiscountPaise():invoice.getDiscountPaise());view.put("serviceChargePaise",invoice==null?order.getServiceChargePaise():invoice.getServiceChargePaise());view.put("roundingAdjustmentPaise",invoice==null?0:invoice.getRoundingPaise());view.put("totalPaise",total);view.put("paidPaise",paid);view.put("amountDuePaise",due);view.put("runningAmountPaise",total);return view;
	}

	private Map<String, Object> lineView(OrderLineEntity line) { Map<String,Object> view = new LinkedHashMap<>(); view.put("id", line.getId()); view.put("roundId", line.getRoundId()); view.put("name", line.getNameSnapshot()); view.put("quantity", line.getQty()); view.put("unitPaise", line.getUnitPaise()); view.put("linePaise", line.getLinePaise()); view.put("status", line.getFulfilmentStatus()); view.put("notes", line.getNotes()); view.put("pickedUpAt", line.getPickedUpAt()); view.put("servedAt", line.getServedAt()); view.put("version", line.getVersion()); return view; }
	private Map<String, Object> kotView(KotEntity kot) { return Map.of("id", kot.getId(), "roundId", kot.getRoundId(), "kotNumber", kot.getKotNumber(), "status", kot.getStatus()); }
	private Map<String, Object> notificationView(WaiterNotificationEntity n) { Map<String,Object> view = new LinkedHashMap<>(); view.put("id", n.getId()); view.put("eventType", n.getEventType()); view.put("orderId", n.getOrderId()); view.put("tableId", n.getTableId()); view.put("kotId", n.getKotId()); view.put("relatedItemIds", n.getRelatedItemIds()); view.put("message", n.getMessage()); view.put("destination", n.getDestination()); view.put("acknowledged", n.isAcknowledged()); view.put("createdAt", n.getCreatedAt()); return view; }
	private List<Map<String,Object>> timeline(OrderEntity order) { List<Map<String,Object>> result = new ArrayList<>(); result.add(Map.of("type", "ORDER_OPENED", "at", order.getCreatedAt(), "label", "Order opened")); for (KotEntity kot : kots.findByOrderId(order.getId())) result.add(Map.of("type", "KOT", "label", "KOT-" + kot.getKotNumber() + " · " + kot.getStatus())); return result; }
	private OrderLineEntity requireLine(UUID orderId, UUID itemId) { OrderLineEntity line = lines.findById(itemId).orElseThrow(() -> ApiException.notFound("ORDER_ITEM", "Order item not found")); if (!orderId.equals(line.getOrderId())) throw ApiException.bad("ORDER_ITEM", "Item does not belong to this order"); return line; }
	private OrderEntity requireOrder(UUID orderId, UUID outletId) { requireOutlet(outletId); OrderEntity order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("ORDER", "Order not found")); if (!outletId.equals(order.getOutletId())) throw ApiException.bad("ORDER_OUTLET", "Order does not belong to this outlet"); return order; }
	private void requireServiceOwner(OrderEntity order) { var p=TenantContext.require(); if (p.hasRole("WAITER") && order.getAssignedWaiterId()!=null && !order.getAssignedWaiterId().equals(p.userId()) && !(p.hasRole("OWNER")||p.hasRole("MANAGER")||p.hasRole("SHIFT_MANAGER"))) throw ApiException.forbidden("WAITER_ASSIGNMENT", "This table is assigned to another waiter"); }
	private void requireOutlet(UUID outletId) { var p = TenantContext.require(); if (p.isGuest()) throw ApiException.forbidden("STAFF_ONLY", "Waiter operations are staff only"); if (p.outletIds() == null || !p.outletIds().contains(outletId)) throw ApiException.forbidden("OUTLET_ACCESS", "You do not have access to this outlet"); }
	private void recalculateKot(UUID roundId) { KotEntity kot = kots.findByRoundId(roundId).orElseThrow(() -> ApiException.notFound("KOT", "KOT not found")); kot.setStatus(aggregate(lines.findByRoundId(roundId))); kots.save(kot); }
	private static long count(List<OrderLineEntity> lines, String status) { return lines.stream().filter(line -> status.equals(line.getFulfilmentStatus())).count(); }
	private static String aggregate(List<OrderLineEntity> values) { List<OrderLineEntity> active = values.stream().filter(line -> !"CANCELLED".equals(line.getFulfilmentStatus())).toList(); if (active.isEmpty()) return "CANCELLED"; long served = count(active,"SERVED"), picked = count(active,"PICKED_UP"), ready = count(active,"READY_FOR_PICKUP"), preparing = count(active,"PREPARING"); int total = active.size(); if (served == total) return "SERVED"; if (served > 0) return "PARTIALLY_SERVED"; if (picked + served == total) return "PICKED_UP"; if (picked > 0) return "PARTIALLY_PICKED_UP"; if (ready + picked + served == total) return "READY"; if (ready > 0) return "PARTIALLY_READY"; if (preparing > 0) return "PREPARING"; return "NEW"; }
	private String waiterName(UUID userId) { List<String> names=jdbc.query("select name from users where id=?",(rs,n)->rs.getString(1),userId); return names.isEmpty()?"Unknown waiter":names.getFirst(); }
	private boolean validWaiter(UUID outletId,UUID userId){if(userId==null)return false;Integer count=jdbc.queryForObject("""
		select count(*) from users u join user_outlets uo on uo.user_id=u.id and uo.outlet_id=? join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id and r.code='WAITER'
		where u.id=? and u.status='ACTIVE' and not exists(select 1 from user_roles x join roles xr on xr.id=x.role_id where x.user_id=u.id and xr.code='OWNER')
		""",Integer.class,outletId,userId);return count!=null&&count>0;}
}
