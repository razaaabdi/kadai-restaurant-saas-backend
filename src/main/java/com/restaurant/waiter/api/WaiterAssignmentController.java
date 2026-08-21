package com.restaurant.waiter.api;

import com.restaurant.waiter.application.WaiterAssignmentService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outlets/{outletId}")
public class WaiterAssignmentController {
	private final WaiterAssignmentService assignments;
	public WaiterAssignmentController(WaiterAssignmentService assignments){this.assignments=assignments;}
	@GetMapping("/waiters/availability") public List<Map<String,Object>> availability(@PathVariable UUID outletId){return assignments.availability(outletId);}
	@GetMapping("/waiters") public Map<String,Object> waiters(@PathVariable UUID outletId,
			@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,
			@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="ALL") String availability,
			@RequestParam(defaultValue="name") String sortBy,@RequestParam(defaultValue="asc") String sortOrder,
			@RequestParam(required=false) UUID floorId){return assignments.directory(outletId,page,pageSize,search,availability,sortBy,sortOrder,floorId);}
	@GetMapping("/waiters/{waiterId}") public Map<String,Object> waiter(@PathVariable UUID outletId,@PathVariable UUID waiterId){return assignments.waiter(outletId,waiterId);}
	@GetMapping("/waiters/{waiterId}/assignments") public List<Map<String,Object>> waiterAssignments(@PathVariable UUID outletId,@PathVariable UUID waiterId){return assignments.waiterAssignments(outletId,waiterId);}
	@GetMapping("/tables/{tableId}/assignment") public Map<String,Object> tableAssignment(@PathVariable UUID outletId,@PathVariable UUID tableId){return assignments.tableAssignment(outletId,tableId);}
	@GetMapping("/orders/{orderId}/assignment") public Map<String,Object> orderAssignment(@PathVariable UUID outletId,@PathVariable UUID orderId){return assignments.orderAssignment(outletId,orderId);}
	@PostMapping("/orders/{orderId}/assign-waiter") public Map<String,Object> assign(@PathVariable UUID outletId,@PathVariable UUID orderId,@RequestBody Map<String,Object> body){return assignments.assign(outletId,orderId,uuid(body,"waiterId"),Boolean.TRUE.equals(body.get("requireAcceptance")));}
	@PostMapping("/orders/{orderId}/transfer-waiter") public Map<String,Object> transfer(@PathVariable UUID outletId,@PathVariable UUID orderId,@RequestBody Map<String,Object> body){return assignments.requestTransfer(outletId,orderId,uuid(body,"waiterId"),body.get("reason")==null?null:body.get("reason").toString(),Boolean.TRUE.equals(body.get("force")));}
	@PostMapping("/assignments/{assignmentId}/accept") public Map<String,Object> accept(@PathVariable UUID outletId,@PathVariable UUID assignmentId,@RequestHeader("If-Match") long version){return assignments.accept(outletId,assignmentId,version);}
	@PostMapping("/assignments/{assignmentId}/accept-transfer") public Map<String,Object> acceptTransfer(@PathVariable UUID outletId,@PathVariable UUID assignmentId,@RequestHeader("If-Match") long version){return assignments.accept(outletId,assignmentId,version);}
	@PostMapping("/assignments/{assignmentId}/reject-transfer") public Map<String,Object> rejectTransfer(@PathVariable UUID outletId,@PathVariable UUID assignmentId,@RequestHeader("If-Match") long version,@RequestBody(required=false) Map<String,Object> body){return assignments.rejectTransfer(outletId,assignmentId,version,body==null?null:String.valueOf(body.get("reason")));}
	@PostMapping("/assignments/{assignmentId}/request-transfer") public Map<String,Object> requestTransfer(@PathVariable UUID outletId,@PathVariable UUID assignmentId,@RequestBody Map<String,Object> body){return assignments.requestTransferByAssignment(outletId,assignmentId,uuid(body,"waiterId"),body.get("reason")==null?null:body.get("reason").toString(),false);}
	@PostMapping("/assignments/{assignmentId}/force-transfer") public Map<String,Object> forceTransfer(@PathVariable UUID outletId,@PathVariable UUID assignmentId,@RequestBody Map<String,Object> body){return assignments.requestTransferByAssignment(outletId,assignmentId,uuid(body,"waiterId"),body.get("reason")==null?null:body.get("reason").toString(),true);}
	@PostMapping("/waiters/{waiterId}/work-status") public Map<String,Object> workStatus(@PathVariable UUID outletId,@PathVariable UUID waiterId,@RequestBody Map<String,Object> body){Integer capacity=body.get("capacity") instanceof Number n?n.intValue():null;return assignments.setWorkStatus(outletId,waiterId,body.get("status")==null?null:body.get("status").toString(),capacity,Boolean.TRUE.equals(body.get("force")));}
	private static UUID uuid(Map<String,Object> body,String key){try{return UUID.fromString(String.valueOf(body.get(key)));}catch(Exception e){throw com.restaurant.platform.api.ApiException.bad("INVALID_ID",key+" must be a valid UUID");}}
}
