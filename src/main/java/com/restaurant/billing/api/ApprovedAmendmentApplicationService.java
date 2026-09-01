package com.restaurant.billing.api;

import com.restaurant.billing.infrastructure.InvoiceAmendmentRepository;
import com.restaurant.platform.api.ApiException;
import com.restaurant.platform.api.AuditWriter;
import com.restaurant.platform.api.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The only path that may reopen a billed order after an invoice was generated. */
@Service
public class ApprovedAmendmentApplicationService {
 private final InvoiceAmendmentRepository amendments; private final JdbcTemplate jdbc; private final BillingFacade billing; private final PasswordEncoder encoder; private final AuditWriter audit;
 public ApprovedAmendmentApplicationService(InvoiceAmendmentRepository a,JdbcTemplate j,BillingFacade b,PasswordEncoder e,AuditWriter w){amendments=a;jdbc=j;billing=b;encoder=e;audit=w;}
 @Transactional public Map<String,Object> apply(UUID amendmentId,String token){var p=TenantContext.require();var a=amendments.findById(amendmentId).orElseThrow(()->ApiException.notFound("AMENDMENT","Not found"));if(!p.tenantId().equals(a.getTenantId())||!"APPROVED".equals(a.getStatus())||a.getApprovalExpiresAt()==null||a.getApprovalExpiresAt().isBefore(Instant.now())||token==null||a.getApprovalTokenHash()==null||!encoder.matches(token,a.getApprovalTokenHash()))throw ApiException.conflict("AMENDMENT_APPROVAL","Approval is invalid or expired");var invoice=billing.require(a.getInvoiceId());UUID orderId=invoice.getOrderId();Integer found=jdbc.queryForObject("select count(*) from orders where id=? and tenant_id=? and status='BILLED'",Integer.class,orderId,p.tenantId());if(found==null||found==0)throw ApiException.conflict("AMENDMENT_STATE","Order is no longer billed");billing.voidUnpaidForRevision(orderId);int updated=jdbc.update("update orders set status='READY',guest_frozen=false,version=version+1 where id=? and tenant_id=? and status='BILLED'",orderId,p.tenantId());if(updated!=1)throw ApiException.conflict("AMENDMENT_STATE","Order changed; reload and try again");a.setStatus("APPLIED");a.setNewTotalPaise(invoice.getTotalPaise());a.setAppliedAt(Instant.now());a.setApprovalTokenHash(null);a.setApprovalExpiresAt(null);amendments.save(a);audit.write("BILL_AMENDMENT_APPLIED","INVOICE",a.getInvoiceId(),"amendment="+amendmentId);return Map.of("orderId",orderId,"status","READY");}
}
