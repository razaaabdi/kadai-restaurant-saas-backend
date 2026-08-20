package com.restaurant.payment.api;

import com.restaurant.platform.api.IdempotencyService;
import com.restaurant.platform.api.TenantContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
	private final PaymentFacade payments;
	private final IdempotencyService idempotency;

	public PaymentController(PaymentFacade payments, IdempotencyService idempotency) {
		this.payments = payments;
		this.idempotency = idempotency;
	}

	@PostMapping
	public ResponseEntity<String> record(@RequestHeader("Idempotency-Key") String key, @RequestBody String raw) {
		return idempotency.run(TenantContext.require().tenantId(), key, raw, () -> {
			var body = new org.springframework.boot.json.JacksonJsonParser().parseMap(raw);
			var res = payments.record(java.util.UUID.fromString((String) body.get("invoiceId")),
					(String) body.get("method"),
					((Number) body.get("amountPaise")).longValue());
			String json = "{\"paymentId\":\"" + res.get("paymentId") + "\",\"invoicePaid\":" + res.get("invoicePaid")
					+ ",\"changePaise\":" + res.get("changePaise") + ",\"orderId\":\"" + res.get("orderId") + "\"}";
			return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
		});
	}
}
