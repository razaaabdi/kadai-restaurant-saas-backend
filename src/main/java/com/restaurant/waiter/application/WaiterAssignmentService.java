package com.restaurant.waiter.application;

import com.restaurant.order.infrastructure.OrderEntity;
import com.restaurant.order.infrastructure.OrderRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.AuditWriter;
import com.restaurant.platform.api.DineInOrderOpened;
import com.restaurant.platform.api.InvoicePaid;
import com.restaurant.platform.api.OrderClosed;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.waiter.infrastructure.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WaiterAssignmentService {
	private static final List<String> ACTIVE = List.of("PENDING_ACCEPTANCE","ASSIGNED","TRANSFER_REQUESTED");
	private final WaiterAssignmentRepository assignments; private final WaiterWorkProfileRepository profiles;
	private final OrderRepository orders; private final JdbcTemplate jdbc;
	private final WaiterNotificationRepository notifications; private final AuditWriter audit;
	public WaiterAssignmentService(WaiterAssignmentRepository assignments, WaiterWorkProfileRepository profiles,
			OrderRepository orders, JdbcTemplate jdbc,
			WaiterNotificationRepository notifications, AuditWriter audit) {
		this.assignments=assignments; this.profiles=profiles; this.orders=orders; this.jdbc=jdbc;
		this.notifications=notifications; this.audit=audit;
	}

	@EventListener
	@Transactional
	public void autoAssign(DineInOrderOpened event) {
		if (event.waiterId()==null || event.tableId()==null || !hasExplicitWaiterRole(event.outletId(),event.waiterId()) || assignments.findFirstByOrderIdAndStatusIn(event.orderId(), ACTIVE).isPresent()) return;
		create(event.outletId(), event.orderId(), event.waiterId(), false);
	}

	@EventListener
	@Transactional
	public void onPaid(InvoicePaid event) { /* Assignment remains active until the paid order is explicitly closed. */ }

	@EventListener
	@Transactional
	public void onClosed(OrderClosed event){completeForOrder(event.orderId());}

	@Transactional
	public List<Map<String,Object>> availability(UUID outletId) {
		requireDirectoryAccess(outletId);
		ensureWaiterProfiles(outletId);
		return profiles.findByOutletId(outletId).stream().filter(profile->hasExplicitWaiterRole(outletId,profile.getWaiterId())).map(this::profileView).toList();
	}

	@Transactional
	public Map<String,Object> directory(UUID outletId,int requestedPage,int requestedPageSize,String search,String availability,String sortBy,String sortOrder,UUID floorId) {
		requireDirectoryAccess(outletId); ensureWaiterProfiles(outletId);
		if(requestedPage<1) throw ApiException.bad("INVALID_PAGE","page must be at least 1");
		if(requestedPageSize<1||requestedPageSize>100) throw ApiException.bad("INVALID_PAGE_SIZE","pageSize must be between 1 and 100");
		String normalizedAvailability=availability==null||availability.isBlank()?"ALL":availability.toUpperCase();
		if(!List.of("ALL","AVAILABLE","BUSY","ON_BREAK","OFFLINE").contains(normalizedAvailability)) throw ApiException.bad("INVALID_AVAILABILITY","Unsupported waiter availability filter");
		Map<String,String> sorts=Map.of("name","display_name","availability","effective_availability","activeTableCount","active_table_count","remainingCapacity","remaining_capacity","lastStatusUpdate","updated_at");
		String orderColumn=sorts.get(sortBy==null?"name":sortBy); if(orderColumn==null) throw ApiException.bad("INVALID_SORT","Unsupported waiter sort field");
		String direction="desc".equalsIgnoreCase(sortOrder)?"DESC":"ASC";
		String term=search==null?"":search.trim(); if(term.length()>80) throw ApiException.bad("INVALID_SEARCH","search must be 80 characters or fewer");
		String base="""
			WITH waiter_rows AS (
			 SELECT u.id waiter_id,u.name display_name,u.employee_code,p.manual_status,p.capacity,p.version,p.updated_at,
			  count(a.id) FILTER (WHERE a.status IN ('PENDING_ACCEPTANCE','ASSIGNED','TRANSFER_REQUESTED')) active_table_count,
			  CASE WHEN p.manual_status<>'ONLINE' THEN 'UNAVAILABLE' WHEN count(a.id) FILTER (WHERE a.status IN ('PENDING_ACCEPTANCE','ASSIGNED','TRANSFER_REQUESTED'))>=p.capacity THEN 'BUSY' ELSE 'AVAILABLE' END effective_availability,
			  coalesce(array_remove(array_agg(DISTINCT t.id) FILTER (WHERE a.status IN ('PENDING_ACCEPTANCE','ASSIGNED','TRANSFER_REQUESTED')),NULL),ARRAY[]::uuid[]) assigned_table_ids,
			  coalesce(array_remove(array_agg(DISTINCT t.code) FILTER (WHERE a.status IN ('PENDING_ACCEPTANCE','ASSIGNED','TRANSFER_REQUESTED')),NULL),ARRAY[]::varchar[]) assigned_table_codes,
			  coalesce(array_remove(array_agg(DISTINCT ar.name) FILTER (WHERE a.status IN ('PENDING_ACCEPTANCE','ASSIGNED','TRANSFER_REQUESTED')),NULL),ARRAY[]::varchar[]) sections
			 FROM waiter_work_profiles p JOIN users u ON u.id=p.waiter_id JOIN user_outlets uo ON uo.user_id=u.id AND uo.outlet_id=p.outlet_id
			 JOIN user_roles ur ON ur.user_id=u.id JOIN roles wr ON wr.id=ur.role_id AND wr.code='WAITER'
			 LEFT JOIN waiter_assignments a ON a.outlet_id=p.outlet_id AND a.waiter_id=u.id
			 LEFT JOIN tables t ON t.id=a.table_id LEFT JOIN areas ar ON ar.id=t.area_id
			 WHERE p.outlet_id=? AND u.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM user_roles our JOIN roles owner_role ON owner_role.id=our.role_id WHERE our.user_id=u.id AND owner_role.code='OWNER')
			 GROUP BY u.id,u.name,u.employee_code,p.manual_status,p.capacity,p.version,p.updated_at
			)
			SELECT * FROM waiter_rows WHERE (?='' OR lower(display_name) LIKE lower(?) OR lower(coalesce(employee_code,'')) LIKE lower(?) OR EXISTS(SELECT 1 FROM unnest(assigned_table_codes) c WHERE lower(c) LIKE lower(?)))
			 AND (?='ALL' OR effective_availability=? OR (?='ON_BREAK' AND manual_status='ON_BREAK') OR (?='OFFLINE' AND manual_status='OFFLINE'))
			""";
		if(floorId!=null) base += " AND EXISTS(SELECT 1 FROM waiter_assignments fa JOIN tables ft ON ft.id=fa.table_id WHERE fa.waiter_id=waiter_rows.waiter_id AND fa.outlet_id=? AND ft.area_id=? AND fa.status IN ('PENDING_ACCEPTANCE','ASSIGNED','TRANSFER_REQUESTED'))";
		List<Object> args=new ArrayList<>(List.of(outletId,term,"%"+term+"%","%"+term+"%","%"+term+"%",normalizedAvailability,normalizedAvailability,normalizedAvailability,normalizedAvailability)); if(floorId!=null){args.add(outletId);args.add(floorId);}
		String countSql="SELECT count(*) FROM ("+base+") filtered"; long total=jdbc.queryForObject(countSql,Long.class,args.toArray()); int totalPages=Math.max(1,(int)Math.ceil(total/(double)requestedPageSize));int page=Math.min(requestedPage,totalPages);int offset=(page-1)*requestedPageSize;
		String dataSql=base+" ORDER BY "+orderColumn+" "+direction+", waiter_id ASC LIMIT ? OFFSET ?";List<Object> dataArgs=new ArrayList<>(args);dataArgs.add(requestedPageSize);dataArgs.add(offset);
		List<Map<String,Object>> items=jdbc.query(dataSql,(rs,n)->{Map<String,Object> m=new LinkedHashMap<>();long active=rs.getLong("active_table_count");m.put("id",UUID.fromString(rs.getString("waiter_id")));m.put("displayName",rs.getString("display_name"));m.put("employeeCode",rs.getString("employee_code"));m.put("role","WAITER");m.put("avatarUrl",null);m.put("manualWorkStatus",rs.getString("manual_status"));m.put("effectiveAvailability",rs.getString("effective_availability"));m.put("activeTableCount",active);m.put("maxActiveTables",rs.getInt("capacity"));m.put("remainingCapacity",Math.max(0,rs.getInt("capacity")-active));m.put("assignedTableIds",List.of((Object[])rs.getArray("assigned_table_ids").getArray()));m.put("assignedTableCodes",List.of((Object[])rs.getArray("assigned_table_codes").getArray()));m.put("sections",List.of((Object[])rs.getArray("sections").getArray()));m.put("lastStatusUpdatedAt",rs.getTimestamp("updated_at").toInstant());m.put("version",rs.getLong("version"));return m;},dataArgs.toArray());
		Map<String,Object> pagination=new LinkedHashMap<>();pagination.put("page",page);pagination.put("pageSize",requestedPageSize);pagination.put("totalItems",total);pagination.put("totalPages",totalPages);pagination.put("hasNextPage",page<totalPages);pagination.put("hasPreviousPage",page>1);return Map.of("items",items,"pagination",pagination);
	}
	public Map<String,Object> waiter(UUID outletId, UUID waiterId) { requireDirectoryAccess(outletId); return profileView(profile(outletId,waiterId)); }

	public List<Map<String,Object>> waiterAssignments(UUID outletId, UUID waiterId) {
		requireDirectoryAccess(outletId); if(!isManager()&&!waiterId.equals(TenantContext.require().userId())) throw ApiException.forbidden("ASSIGNMENT_HISTORY","Only managers can view another waiter's assignment history"); return assignments.findByOutletIdAndWaiterIdOrderByAssignedAtDesc(outletId, waiterId).stream().map(this::view).toList();
	}

	public Map<String,Object> tableAssignment(UUID outletId, UUID tableId) {
		requireOutlet(outletId); return assignments.findByOutletIdOrderByAssignedAtDesc(outletId).stream()
				.filter(a -> tableId.equals(a.getTableId()) && ACTIVE.contains(a.getStatus())).findFirst().map(this::view)
				.orElseThrow(() -> ApiException.notFound("ASSIGNMENT", "This table has no active waiter assignment"));
	}
	public Map<String,Object> orderAssignment(UUID outletId, UUID orderId) { requireOutlet(outletId); requireOrder(outletId,orderId); return view(requireActive(orderId)); }

	@Transactional
	public Map<String,Object> assign(UUID outletId, UUID orderId, UUID waiterId, boolean requireAcceptance) {
		requireManagerOrSelf(waiterId); return view(create(outletId, orderId, waiterId, requireAcceptance));
	}

	@Transactional
	public Map<String,Object> requestTransfer(UUID outletId, UUID orderId, UUID targetWaiterId, String reason, boolean force) {
		OrderEntity order=requireOrder(outletId, orderId); WaiterAssignmentEntity current=requireActive(orderId);
		var p=TenantContext.require(); boolean manager=isManager();
		if (!manager && !p.userId().equals(current.getWaiterId())) throw ApiException.forbidden("ASSIGNMENT_OWNER", "Only the assigned waiter or a manager can transfer this table");
		ensureEligible(outletId,targetWaiterId);
		if (targetWaiterId.equals(current.getWaiterId())) throw ApiException.conflict("SAME_WAITER", "Choose a different waiter");
		if (force) {
			if (!manager) throw ApiException.forbidden("MANAGER_REQUIRED", "Only a manager can force a transfer");
			return view(completeTransfer(current, order, targetWaiterId, p.userId()));
		}
		current.setStatus("TRANSFER_REQUESTED"); current.setTransferToWaiterId(targetWaiterId); current.setTransferRequestedBy(p.userId()); current.setTransferRequestedAt(Instant.now()); current.setTransferReason(normalizeReason(reason)); assignments.save(current);
		notify(current, targetWaiterId, "TRANSFER_REQUESTED", "A table transfer is waiting for your acceptance", "TRANSFER:"+current.getId()+":"+current.getVersion());
		audit.write("WAITER_TRANSFER_REQUESTED","WAITER_ASSIGNMENT",current.getId(),"to="+targetWaiterId); return view(current);
	}
	public Map<String,Object> requestTransferByAssignment(UUID outletId,UUID assignmentId,UUID targetWaiterId,String reason,boolean force){requireOutlet(outletId);WaiterAssignmentEntity assignment=assignments.findById(assignmentId).orElseThrow(()->ApiException.notFound("ASSIGNMENT","Assignment not found"));if(!outletId.equals(assignment.getOutletId())||!ACTIVE.contains(assignment.getStatus()))throw ApiException.notFound("ASSIGNMENT","Active assignment not found");return requestTransfer(outletId,assignment.getOrderId(),targetWaiterId,reason,force);}

	@Transactional
	public Map<String,Object> accept(UUID outletId, UUID assignmentId, long expectedVersion) {
		requireOutlet(outletId); WaiterAssignmentEntity current=assignments.findById(assignmentId).orElseThrow(() -> ApiException.notFound("ASSIGNMENT","Assignment not found"));
		if (!outletId.equals(current.getOutletId())) throw ApiException.bad("ASSIGNMENT_OUTLET","Assignment does not belong to this outlet");
		if (current.getVersion()!=expectedVersion) throw ApiException.conflict("STALE_ASSIGNMENT","Assignment changed; refresh and try again");
		UUID actor=TenantContext.require().userId();
		if ("PENDING_ACCEPTANCE".equals(current.getStatus())) { if(!isManager()&&!actor.equals(current.getWaiterId())) throw ApiException.forbidden("ASSIGNMENT_TARGET","Only the assigned waiter can accept this table"); current.setStatus("ASSIGNED");current.setAcceptedAt(Instant.now());assignments.save(current);recordEvent(current,"ASSIGNMENT_ACCEPTED",null,current.getWaiterId(),"PENDING_ACCEPTANCE","ASSIGNED",null);return view(current); }
		if (!"TRANSFER_REQUESTED".equals(current.getStatus())) throw ApiException.conflict("TRANSFER_STATE","This transfer is no longer pending");
		if (!isManager() && !actor.equals(current.getTransferToWaiterId())) throw ApiException.forbidden("TRANSFER_TARGET","Only the requested waiter can accept this transfer");
		return view(completeTransfer(current, requireOrder(outletId,current.getOrderId()),current.getTransferToWaiterId(),actor));
	}

	@Transactional
	public Map<String,Object> rejectTransfer(UUID outletId, UUID assignmentId, long expectedVersion, String reason) { requireOutlet(outletId);WaiterAssignmentEntity current=assignments.findById(assignmentId).orElseThrow(()->ApiException.notFound("ASSIGNMENT","Assignment not found"));if(!outletId.equals(current.getOutletId()))throw ApiException.notFound("ASSIGNMENT","Assignment not found");if(current.getVersion()!=expectedVersion)throw ApiException.conflict("STALE_ASSIGNMENT","Assignment changed; refresh and try again");if(!"TRANSFER_REQUESTED".equals(current.getStatus()))throw ApiException.conflict("TRANSFER_STATE","This transfer is no longer pending");UUID actor=TenantContext.require().userId();if(!isManager()&&!actor.equals(current.getTransferToWaiterId()))throw ApiException.forbidden("TRANSFER_TARGET","Only the requested waiter can reject this transfer");UUID target=current.getTransferToWaiterId();current.setStatus("ASSIGNED");current.setTransferToWaiterId(null);current.setTransferReason(normalizeReason(reason));assignments.save(current);notify(current,current.getWaiterId(),"TRANSFER_REJECTED","The requested table transfer was declined","TRANSFER_REJECTED:"+current.getId()+":"+current.getVersion());recordEvent(current,"TRANSFER_REJECTED",current.getWaiterId(),target,"TRANSFER_REQUESTED","ASSIGNED",reason);audit.write("WAITER_TRANSFER_REJECTED","WAITER_ASSIGNMENT",current.getId(),"target="+target);return view(current);}

	@Transactional
	public Map<String,Object> setWorkStatus(UUID outletId, UUID waiterId, String status, Integer capacity, boolean force) {
		requireManagerOrSelf(waiterId); String normalized=status==null?"ONLINE":status.toUpperCase();
		if (!List.of("ONLINE","ON_BREAK","OFFLINE").contains(normalized)) throw ApiException.bad("WORK_STATUS","Status must be ONLINE, ON_BREAK, or OFFLINE");
		WaiterWorkProfileEntity profile=profile(outletId,waiterId); long active=activeCount(outletId,waiterId);
		if (!"ONLINE".equals(normalized) && active>0 && !(force && isManager())) throw ApiException.conflict("ACTIVE_ASSIGNMENTS","Transfer active tables before going unavailable");
		if (capacity!=null) { if (capacity<1 || capacity>50) throw ApiException.bad("CAPACITY","Capacity must be between 1 and 50"); if (!isManager()) throw ApiException.forbidden("MANAGER_REQUIRED","Only a manager can change capacity"); profile.setCapacity(capacity); }
		profile.setManualStatus(normalized); profiles.save(profile); audit.write("WAITER_WORK_STATUS","WAITER_PROFILE",profile.getId(),normalized); return profileView(profile);
	}

	@Transactional
	public void completeForOrder(UUID orderId) {
		assignments.findFirstByOrderIdAndStatusIn(orderId,ACTIVE).ifPresent(a->{String previous=a.getStatus();a.setStatus("COMPLETED");a.setReleasedAt(Instant.now());assignments.save(a);recordEvent(a,"ASSIGNMENT_COMPLETED",a.getWaiterId(),null,previous,"COMPLETED",null);audit.write("WAITER_ASSIGNMENT_COMPLETED","WAITER_ASSIGNMENT",a.getId(),"order="+orderId);});
	}

	private WaiterAssignmentEntity create(UUID outletId, UUID orderId, UUID waiterId, boolean pending) {
		OrderEntity order=requireOrder(outletId,orderId); if(order.getTableId()==null) throw ApiException.bad("DINE_IN_ONLY","Only dine-in orders can be assigned");
		if(assignments.findFirstByOrderIdAndStatusIn(orderId,ACTIVE).isPresent()) throw ApiException.conflict("ASSIGNMENT_EXISTS","This order already has a primary waiter");
		ensureEligible(outletId,waiterId); var p=TenantContext.require(); WaiterAssignmentEntity a=new WaiterAssignmentEntity(); a.setTenantId(p.tenantId());a.setOutletId(outletId);a.setTableId(order.getTableId());a.setOrderId(orderId);a.setWaiterId(waiterId);a.setAssignedBy(p.userId());a.setStatus(pending?"PENDING_ACCEPTANCE":"ASSIGNED");if(!pending)a.setAcceptedAt(Instant.now()); assignments.saveAndFlush(a); order.setAssignedWaiterId(waiterId);orders.save(order); notify(a,waiterId,pending?"ASSIGNMENT_PENDING":"ASSIGNED",pending?"A table assignment needs your acceptance":"You are now serving this table","ASSIGNED:"+a.getId()); audit.write("WAITER_ASSIGNED","WAITER_ASSIGNMENT",a.getId(),"waiter="+waiterId); return a;
	}

	private WaiterAssignmentEntity completeTransfer(WaiterAssignmentEntity old, OrderEntity order, UUID target, UUID actor) {
		ensureEligible(old.getOutletId(),target); old.setStatus("TRANSFERRED");old.setReleasedAt(Instant.now());assignments.saveAndFlush(old);
		WaiterAssignmentEntity next=new WaiterAssignmentEntity();next.setTenantId(TenantContext.require().tenantId());next.setOutletId(old.getOutletId());next.setTableId(old.getTableId());next.setOrderId(old.getOrderId());next.setWaiterId(target);next.setAssignedBy(actor);next.setStatus("ASSIGNED");next.setAcceptedAt(Instant.now());next.setPreviousAssignmentId(old.getId());assignments.saveAndFlush(next);order.setAssignedWaiterId(target);orders.save(order);notify(next,target,"TRANSFER_ACCEPTED","Table responsibility has been transferred to you","TRANSFERRED:"+next.getId());notify(next,old.getWaiterId(),"TRANSFER_COMPLETED","Your table transfer is complete","TRANSFERRED_FROM:"+old.getId());recordEvent(next,"TRANSFER_COMPLETED",old.getWaiterId(),target,"TRANSFER_REQUESTED","ASSIGNED",old.getTransferReason());audit.write("WAITER_TRANSFER_COMPLETED","WAITER_ASSIGNMENT",next.getId(),"from="+old.getWaiterId()+",to="+target);return next;
	}

	private void ensureEligible(UUID outletId, UUID waiterId){List<String> statuses=jdbc.query("select status from users where id=?",(rs,n)->rs.getString(1),waiterId);if(statuses.isEmpty())throw ApiException.notFound("WAITER","Waiter not found");if(!"ACTIVE".equals(statuses.getFirst()))throw ApiException.conflict("WAITER_INACTIVE","This waiter account is inactive");if(!hasExplicitWaiterRole(outletId,waiterId))throw ApiException.bad("WAITER_ROLE","Only an active waiter assigned to this outlet can serve a table");WaiterWorkProfileEntity p=profile(outletId,waiterId);if(!"ONLINE".equals(p.getManualStatus()))throw ApiException.conflict("WAITER_UNAVAILABLE","Waiter is offline or on break");if(activeCount(outletId,waiterId)>=p.getCapacity())throw ApiException.conflict("WAITER_CAPACITY","Waiter has reached active-table capacity");}
	private boolean hasExplicitWaiterRole(UUID outletId,UUID userId){Integer count=jdbc.queryForObject("""
		select count(*) from users u join user_outlets uo on uo.user_id=u.id and uo.outlet_id=? join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id and r.code='WAITER'
		where u.id=? and u.status='ACTIVE' and not exists(select 1 from user_roles x join roles xr on xr.id=x.role_id where x.user_id=u.id and xr.code='OWNER')
		""",Integer.class,outletId,userId);return count!=null&&count>0;}
	private WaiterWorkProfileEntity profile(UUID outletId,UUID waiterId){return profiles.findByOutletIdAndWaiterId(outletId,waiterId).orElseGet(()->{WaiterWorkProfileEntity p=new WaiterWorkProfileEntity();p.setTenantId(TenantContext.require().tenantId());p.setOutletId(outletId);p.setWaiterId(waiterId);return profiles.save(p);});}
	private long activeCount(UUID outletId,UUID waiterId){return assignments.countByOutletIdAndWaiterIdAndStatusIn(outletId,waiterId,ACTIVE);}
	private Map<String,Object> profileView(WaiterWorkProfileEntity p){long active=activeCount(p.getOutletId(),p.getWaiterId());String effective=!"ONLINE".equals(p.getManualStatus())?"UNAVAILABLE":active>=p.getCapacity()?"BUSY":"AVAILABLE";Map<String,Object> m=new LinkedHashMap<>();m.put("waiterId",p.getWaiterId());m.put("waiterName",userName(p.getWaiterId()));m.put("avatarUrl",null);m.put("manualStatus",p.getManualStatus());m.put("effectiveStatus",effective);m.put("activeAssignmentCount",active);m.put("capacity",p.getCapacity());m.put("remainingCapacity",Math.max(0,p.getCapacity()-active));if(isManager())m.put("assignedTableIds",assignments.findByOutletIdAndWaiterIdOrderByAssignedAtDesc(p.getOutletId(),p.getWaiterId()).stream().filter(a->ACTIVE.contains(a.getStatus())).map(WaiterAssignmentEntity::getTableId).toList());m.put("version",p.getVersion());return m;}
	private Map<String,Object> view(WaiterAssignmentEntity a){Map<String,Object>m=new LinkedHashMap<>();m.put("assignmentId",a.getId());m.put("outletId",a.getOutletId());m.put("tableId",a.getTableId());m.put("orderId",a.getOrderId());m.put("waiterId",a.getWaiterId());m.put("waiterName",userName(a.getWaiterId()));m.put("status",a.getStatus());m.put("assignedBy",a.getAssignedBy());m.put("assignedAt",a.getAssignedAt());m.put("acceptedAt",a.getAcceptedAt());m.put("releasedAt",a.getReleasedAt());m.put("transferToWaiterId",a.getTransferToWaiterId());m.put("transferReason",a.getTransferReason());m.put("version",a.getVersion());return m;}
	private String userName(UUID userId){List<String> names=jdbc.query("select name from users where id=?",(rs,n)->rs.getString(1),userId);return names.isEmpty()?"Unknown waiter":names.getFirst();}
	private void ensureWaiterProfiles(UUID outletId){jdbc.query("""
		select distinct u.id from users u join user_outlets uo on uo.user_id=u.id and uo.outlet_id=?
		join user_roles ur on ur.user_id=u.id join roles r on r.id=ur.role_id and r.code='WAITER'
		where u.status='ACTIVE' and not exists(select 1 from user_roles x join roles xr on xr.id=x.role_id where x.user_id=u.id and xr.code='OWNER')
		""",(rs,n)->UUID.fromString(rs.getString(1)),outletId).forEach(id->profile(outletId,id));}
	private void notify(WaiterAssignmentEntity a,UUID recipient,String event,String message,String dedupe){if(notifications.existsByDedupeKey(dedupe))return;WaiterNotificationEntity n=new WaiterNotificationEntity();n.setTenantId(TenantContext.require().tenantId());n.setOutletId(a.getOutletId());n.setRecipientUserId(recipient);n.setEventType(event);n.setOrderId(a.getOrderId());n.setTableId(a.getTableId());n.setAssignmentId(a.getId());n.setMessage(message);n.setDestination("/waiter/orders/"+a.getOrderId());n.setDedupeKey(dedupe);notifications.save(n);}
	private void recordEvent(WaiterAssignmentEntity a,String type,UUID previousWaiter,UUID newWaiter,String previousStatus,String newStatus,String reason){jdbc.update("insert into waiter_assignment_events(id,tenant_id,outlet_id,assignment_id,order_id,table_id,event_type,actor_user_id,previous_waiter_id,new_waiter_id,previous_status,new_status,reason) values (gen_random_uuid(),?,?,?,?,?,?,?,?,?,?,?,?)",TenantContext.require().tenantId(),a.getOutletId(),a.getId(),a.getOrderId(),a.getTableId(),type,TenantContext.require().userId(),previousWaiter,newWaiter,previousStatus,newStatus,normalizeReason(reason));}
	private WaiterAssignmentEntity requireActive(UUID orderId){return assignments.findFirstByOrderIdAndStatusIn(orderId,ACTIVE).orElseThrow(()->ApiException.notFound("ASSIGNMENT","Order has no active waiter assignment"));}
	private OrderEntity requireOrder(UUID outletId,UUID orderId){requireOutlet(outletId);OrderEntity o=orders.findById(orderId).orElseThrow(()->ApiException.notFound("ORDER","Order not found"));if(!outletId.equals(o.getOutletId()))throw ApiException.bad("ORDER_OUTLET","Order does not belong to this outlet");return o;}
	private void requireOutlet(UUID outletId){var p=TenantContext.require();if(p.isGuest())throw ApiException.forbidden("STAFF_ONLY","Staff only");if(p.outletIds()==null||!p.outletIds().contains(outletId))throw ApiException.forbidden("OUTLET_ACCESS","You do not have access to this outlet");}
	private void requireDirectoryAccess(UUID outletId){requireOutlet(outletId);var p=TenantContext.require();if(!(p.hasRole("OWNER")||p.hasRole("MANAGER")||p.hasRole("SHIFT_MANAGER")||p.hasRole("WAITER")||p.hasRole("HOST")||p.hasRole("RECEPTIONIST")))throw ApiException.forbidden("WAITER_DIRECTORY","Your role cannot access waiter availability");}
	private boolean isManager(){var p=TenantContext.require();return p.hasRole("OWNER")||p.hasRole("MANAGER")||p.hasRole("SHIFT_MANAGER");}
	private void requireManagerOrSelf(UUID waiterId){if(!isManager()&&!waiterId.equals(TenantContext.require().userId()))throw ApiException.forbidden("MANAGER_REQUIRED","Only a manager can manage another waiter");}
	private static String normalizeReason(String r){if(r==null||r.isBlank())return null;String v=r.trim();if(v.length()>240)throw ApiException.bad("TRANSFER_REASON","Reason must be 240 characters or fewer");return v;}
}
