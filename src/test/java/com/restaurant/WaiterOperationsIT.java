package com.restaurant;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class WaiterOperationsIT extends AbstractIT {
	@Test
	@SuppressWarnings("unchecked")
	void kitchenToWaiterToBillingToCleaningLifecycle() {
		Http api = new Http("http://localhost:" + port);
		Map<String,Object> onboard = api.post("/api/v1/onboarding", "{\"name\":\"Waiter Cafe\",\"slug\":\"waiter-cafe\",\"email\":\"waiter-owner@test.com\",\"password\":\"secret12\",\"ownerName\":\"Owner\"}");
		String outletId = Http.uuid(onboard, "outletId");
		Map<String,Object> login = api.post("/api/v1/auth/login", "{\"email\":\"waiter-owner@test.com\",\"password\":\"secret12\"}");
		api.auth(Http.uuid(login, "accessToken"));
		Map<String,Object> category = api.post("/api/v1/outlets/" + outletId + "/categories", "{\"name\":\"Kitchen\"}");
		Map<String,Object> item = api.post("/api/v1/outlets/" + outletId + "/items", "{\"categoryId\":\"" + Http.uuid(category,"id") + "\",\"name\":\"Service Bowl\",\"pricePaise\":25000,\"available\":true}");
		Map<String,Object> area = api.post("/api/v1/outlets/" + outletId + "/areas", "{\"name\":\"Main\"}");
		Map<String,Object> table = api.post("/api/v1/areas/" + Http.uuid(area,"id") + "/tables", "{\"code\":\"W1\",\"seats\":4}");
		String tableId = Http.uuid(table,"tableId");
		Map<String,Object> order = api.post("/api/v1/outlets/" + outletId + "/orders", "{\"channel\":\"COUNTER_DINE_IN\",\"tableId\":\"" + tableId + "\",\"items\":[{\"menuItemId\":\"" + Http.uuid(item,"itemId") + "\",\"variantId\":\"" + Http.uuid(item,"variantId") + "\",\"quantity\":1,\"notes\":\"No chilli\"}]}", UUID.randomUUID().toString());
		String orderId = Http.uuid(order,"id");
			List<Map<String,Object>> availability = (List<Map<String,Object>>) api.get("/api/v1/outlets/" + outletId + "/waiters/availability").get("list");
			assertThat(availability).noneMatch(waiter -> login.get("userId").equals(waiter.get("waiterId")));
		List<Map<String,Object>> kots = (List<Map<String,Object>>) api.get("/api/v1/orders/" + orderId + "/kots").get("list");
		String kotId = String.valueOf(kots.getFirst().get("id"));
		api.post("/api/v1/kots/" + kotId + "/accept", "{}");
		api.post("/api/v1/kots/" + kotId + "/start-prep", "{}");
			Map<String,Object> detail = api.get("/api/v1/outlets/" + outletId + "/waiter/orders/" + orderId);
			assertThat(detail.get("assignedWaiterId")).isNull();
		List<Map<String,Object>> lines = (List<Map<String,Object>>) detail.get("items");
		String lineId = String.valueOf(lines.getFirst().get("id"));
		assertThat(lines.getFirst().get("status")).isEqualTo("PREPARING");
		api.post("/api/v1/kots/" + kotId + "/items/" + lineId + "/mark-ready", "{}");
		List<Map<String,Object>> notices = (List<Map<String,Object>>) api.get("/api/v1/outlets/" + outletId + "/waiter/notifications").get("list");
		assertThat(notices).anySatisfy(notice -> assertThat(notice.get("eventType")).isEqualTo("KOT_READY_FOR_PICKUP"));
		assertThat(api.postRaw("/api/v1/outlets/" + outletId + "/waiter/orders/" + orderId + "/items/" + lineId + "/serve", "{}", null).getStatusCode().value()).isEqualTo(409);
		api.post("/api/v1/outlets/" + outletId + "/waiter/orders/" + orderId + "/items/" + lineId + "/pickup", "{}");
		api.post("/api/v1/outlets/" + outletId + "/waiter/orders/" + orderId + "/items/" + lineId + "/serve", "{}");
		Map<String,Object> requested = api.post("/api/v1/outlets/" + outletId + "/waiter/orders/" + orderId + "/request-bill", "{}");
		assertThat(requested.get("status")).isEqualTo("BILL_REQUESTED");
		assertThat(requested).doesNotContainKey("invoiceId");
			Map<String,Object> invoice = api.post("/api/v1/outlets/" + outletId + "/waiter/orders/" + orderId + "/invoice", "{\"discountPaise\":0}");
			Map<String,Object> billed = api.get("/api/v1/outlets/" + outletId + "/waiter/orders/" + orderId);
			assertThat(billed).containsEntry("orderStatus","BILLED").containsEntry("tableStatus","OCCUPIED").containsEntry("invoiceStatus","GENERATED").containsEntry("paymentStatus","AWAITING_PAYMENT");
			assertThat(billed.get("totalPaise")).isEqualTo(invoice.get("invoiceTotalPaise"));
			assertThat(billed.get("amountDuePaise")).isEqualTo(invoice.get("invoiceTotalPaise"));
			api.post("/api/v1/payments", "{\"invoiceId\":\"" + Http.uuid(invoice,"invoiceId") + "\",\"method\":\"UPI\",\"amountPaise\":" + invoice.get("invoiceTotalPaise") + "}", UUID.randomUUID().toString());
			Map<String,Object> paid = api.get("/api/v1/outlets/" + outletId + "/waiter/orders/" + orderId);
			assertThat(paid).containsEntry("orderStatus","COMPLETED").containsEntry("tableStatus","CLEANING_REQUIRED").containsEntry("paymentStatus","PAID");
			List<Map<String,Object>> waiterOrders = (List<Map<String,Object>>) api.get("/api/v1/outlets/" + outletId + "/waiter/orders").get("list");
		assertThat(waiterOrders).singleElement().satisfies(row -> assertThat(row.get("tableStatus")).isEqualTo("CLEANING_REQUIRED"));
		api.post("/api/v1/tables/" + tableId + "/start-cleaning", "{}");
		api.post("/api/v1/tables/" + tableId + "/complete-cleaning", "{}");
		List<Map<String,Object>> floor = (List<Map<String,Object>>) api.get("/api/v1/outlets/" + outletId + "/tables").get("list");
		assertThat(floor).singleElement().satisfies(row -> assertThat(row.get("status")).isEqualTo("FREE"));
	}
}
