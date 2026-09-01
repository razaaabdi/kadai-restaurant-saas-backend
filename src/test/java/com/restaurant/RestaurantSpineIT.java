package com.restaurant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantSpineIT extends AbstractIT {

	@Test
	@SuppressWarnings("unchecked")
	void qrGuestPathCounterPathIsolationJoinRotateIdempotencyStock() {
		Http api = new Http("http://localhost:" + port);

		var a = api.post("/api/v1/onboarding", """
				{"name":"Cafe A","slug":"cafe-a","email":"owner-a@test.com","password":"secret12","ownerName":"A"}
				""");
		String outletA = Http.uuid(a, "outletId");
		var loginA = api.post("/api/v1/auth/login", """
				{"email":"owner-a@test.com","password":"secret12"}
				""");
		String tokenA = Http.uuid(loginA, "accessToken");
		api.auth(tokenA);

		var tax = api.post("/api/v1/tax-codes", """
				{"code":"GST5","rateBps":500}
				""");
		var cat = api.post("/api/v1/outlets/" + outletA + "/categories", """
				{"name":"Mains"}
				""");
		var item = api.post("/api/v1/outlets/" + outletA + "/items",
				"{\"categoryId\":\"" + taxId(cat, "id") + "\",\"name\":\"Masala Dosa\",\"pricePaise\":12000,\"taxCodeId\":\""
						+ taxId(tax, "id") + "\",\"availableOnQr\":true}");
		String variantId = Http.uuid(item, "variantId");
		var invItem = api.post("/api/v1/outlets/" + outletA + "/inventory-items", """
				{"name":"Batter","unit":"g"}
				""");
		String invId = Http.uuid(invItem, "id");
		var oilItem = api.post("/api/v1/outlets/" + outletA + "/inventory-items", """
				{"name":"Oil","unit":"ml"}
				""");
		String oilId = Http.uuid(oilItem,"id");
		var invUpdated = api.putRaw("/api/v1/inventory-items/" + invId, """
				{"name":"Dosa Batter","unit":"kg"}
				""", 0);
		assertThat(invUpdated.getStatusCode().is2xxSuccessful()).isTrue();
		var invBody = Http.parse(invUpdated.getBody());
		assertThat(invBody.get("name")).isEqualTo("Dosa Batter");
		assertThat(invBody.get("unit")).isEqualTo("kg");
		assertThat(api.putRaw("/api/v1/inventory-items/" + UUID.randomUUID(), """
				{"name":"Missing","unit":"g"}
				""", 0).getStatusCode().value()).isEqualTo(404);
		api.post("/api/v1/recipes",
				"{\"variantId\":\"" + variantId + "\",\"ingredients\":[{\"inventoryItemId\":\"" + invId + "\",\"qty\":\"100.0000\"},{\"inventoryItemId\":\""+oilId+"\",\"qty\":\"10.0000\"}]}",
				UUID.randomUUID().toString());
		api.post("/api/v1/stock/purchase",
				"{\"outletId\":\"" + outletA + "\",\"inventoryItemId\":\"" + invId + "\",\"qty\":\"1000.0000\"}",
				"purch-1");
		api.post("/api/v1/stock/purchase",
				"{\"outletId\":\"" + outletA + "\",\"inventoryItemId\":\"" + oilId + "\",\"qty\":\"100.0000\"}",
				"purch-oil-1");

		var area = api.post("/api/v1/outlets/" + outletA + "/areas", """
				{"name":"Hall"}
				""");
		var table = api.post("/api/v1/areas/" + taxId(area, "id") + "/tables", """
				{"code":"T1","seats":4}
				""");
		String qr = Http.uuid(table, "token");
		String tableId = Http.uuid(table, "tableId");

		Http guest1 = new Http("http://localhost:" + port);
		var sess1 = guest1.post("/api/v1/public/qr/" + qr + "/sessions", "{}", "sess-1");
		guest1.auth(Http.uuid(sess1, "accessToken"));
		var menu = guest1.get("/api/v1/public/qr/" + qr + "/menu");
		assertThat((List<?>) menu.get("list")).isNotEmpty();

		var round1 = guest1.post("/api/v1/public/qr/" + qr + "/rounds",
				"{\"items\":[{\"variantId\":\"" + variantId + "\",\"qty\":\"1\"}]}", "round-1");
		String orderId = Http.uuid(round1, "id");
		assertThat(round1.get("status")).isEqualTo("KOT_SENT");

		var kots1 = api.get("/api/v1/orders/" + orderId + "/kots");
		assertThat((List<?>) kots1.get("list")).hasSize(1);
		@SuppressWarnings("unchecked") Map<String,Object> firstKot=(Map<String,Object>)((List<?>)kots1.get("list")).getFirst();
		assertThat(((Map<?,?>)firstKot.get("print")).get("latest")).isInstanceOf(Map.class);
		var reprint=api.post("/api/v1/kots/"+firstKot.get("id")+"/reprint", "{\"reason\":\"Kitchen copy damaged\"}");
		assertThat(reprint.get("status")).isEqualTo("FAILED");

		Http guest2 = new Http("http://localhost:" + port);
		var sess2 = guest2.post("/api/v1/public/qr/" + qr + "/sessions", "{}", "sess-2");
		guest2.auth(Http.uuid(sess2, "accessToken"));
		var round2 = guest2.post("/api/v1/public/qr/" + qr + "/rounds",
				"{\"items\":[{\"variantId\":\"" + variantId + "\",\"qty\":\"1\"}]}", "round-2");
		assertThat(Http.uuid(round2, "id")).isEqualTo(orderId);
		assertThat(((Number) round2.get("rounds")).intValue()).isEqualTo(2);
		var kots2 = api.get("/api/v1/orders/" + orderId + "/kots");
		assertThat((List<?>) kots2.get("list")).hasSize(2);

		var bill = guest1.post("/api/v1/public/qr/" + qr + "/request-bill", "{}", "bill-1");
		String invoiceId = Http.uuid(bill, "invoiceId");
		assertThat(bill.get("status")).isEqualTo("BILLED");
		var invoiceDetail = api.get("/api/v1/invoices/" + invoiceId);
		assertThat(((Map<?, ?>) invoiceDetail.get("invoice")).get("invoiceNumber").toString()).startsWith("INV-");
		assertThat((List<?>) invoiceDetail.get("lines")).hasSize(2);
		var invoiceSearch = api.get("/api/v1/outlets/" + outletA + "/invoices?q="
				+ ((Map<?, ?>) invoiceDetail.get("invoice")).get("invoiceNumber"));
		assertThat((List<?>) invoiceSearch.get("content")).hasSize(1);

		var pay1 = api.post("/api/v1/payments",
				"{\"invoiceId\":\"" + invoiceId + "\",\"method\":\"UPI\",\"amountPaise\":" + bill.get("invoiceTotalPaise") + "}",
				"pay-1");
		assertThat(pay1.get("invoicePaid")).isEqualTo(true);
		assertThat((List<?>) api.get("/api/v1/invoices/" + invoiceId).get("payments")).hasSize(1);
		assertThat(api.get("/api/v1/orders/" + orderId).get("status")).isIn("PAID", "COMPLETED");
		var invalidPayment = api.postRaw("/api/v1/payments",
				"{\"invoiceId\":\"" + invoiceId + "\",\"method\":\"BITCOIN\",\"amountPaise\":1}", "invalid-method");
		assertThat(invalidPayment.getStatusCode().value()).isEqualTo(400);
		var payReplay = api.postRaw("/api/v1/payments",
				"{\"invoiceId\":\"" + invoiceId + "\",\"method\":\"UPI\",\"amountPaise\":" + bill.get("invoiceTotalPaise") + "}",
				"pay-1");
		assertThat(payReplay.getStatusCode()).isEqualTo(HttpStatus.OK);

		api.post("/api/v1/outbox/drain", "{}");
		var sales = api.get("/api/v1/outlets/" + outletA + "/daily-sales?date=" + java.time.LocalDate.now(java.time.ZoneOffset.UTC));
		assertThat(((Number) sales.get("ordersCount")).intValue()).isGreaterThanOrEqualTo(1);
		String dashboardDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
		var dashboardSummary = api.get("/api/v1/outlets/" + outletA + "/dashboard/summary?from=" + dashboardDate + "&to=" + dashboardDate);
		assertThat(((Map<?, ?>) dashboardSummary.get("sales")).get("value")).isEqualTo(bill.get("invoiceTotalPaise"));
		assertThat(((Number) ((Map<?, ?>) dashboardSummary.get("orders")).get("value")).longValue()).isGreaterThanOrEqualTo(1);
		assertThat(dashboardSummary.get("userName")).isEqualTo("A");
		assertThat((List<?>) api.get("/api/v1/outlets/" + outletA + "/dashboard/overview?from=" + dashboardDate + "&to=" + dashboardDate).get("points")).isNotEmpty();
		assertThat(api.get("/api/v1/outlets/" + outletA + "/dashboard/order-status?from=" + dashboardDate + "&to=" + dashboardDate).get("counts")).isInstanceOf(Map.class);
		assertThat((List<?>) api.get("/api/v1/outlets/" + outletA + "/dashboard/recent-orders?from=" + dashboardDate + "&to=" + dashboardDate).get("list")).isNotEmpty();
		assertThat((List<?>) api.get("/api/v1/outlets/" + outletA + "/dashboard/search?q=T1").get("list")).isNotEmpty();

		var bal = api.get("/api/v1/stock/balance?outletId=" + outletA + "&inventoryItemId=" + invId);
		assertThat(new java.math.BigDecimal(String.valueOf(bal.get("qty")))).isEqualByComparingTo("1000.0000");

		api.post("/api/v1/tables/" + tableId + "/clear-table", "{}");

		var counter = api.post("/api/v1/outlets/" + outletA + "/orders",
				"{\"channel\":\"TAKEAWAY\",\"items\":[{\"variantId\":\"" + variantId + "\",\"qty\":\"1\"}]}",
				"counter-1");
		String counterOrder = Http.uuid(counter, "id");
		var counterBill = api.post("/api/v1/orders/" + counterOrder + "/request-bill", "{}", "cbill-1");
		api.post("/api/v1/payments",
				"{\"invoiceId\":\"" + Http.uuid(counterBill, "invoiceId") + "\",\"method\":\"CASH\",\"amountPaise\":"
						+ counterBill.get("invoiceTotalPaise") + "}",
				"cpay-1");

		var illegal = api.postRaw("/api/v1/orders/" + counterOrder + "/status", "{\"status\":\"DRAFT\"}", null);
		assertThat(illegal.getStatusCode().value()).isEqualTo(409);

		var b = new Http("http://localhost:" + port).post("/api/v1/onboarding", """
				{"name":"Cafe B","slug":"cafe-b","email":"owner-b@test.com","password":"secret12","ownerName":"B"}
				""");
		var loginB = new Http("http://localhost:" + port).post("/api/v1/auth/login", """
				{"email":"owner-b@test.com","password":"secret12"}
				""");
		Http apiB = new Http("http://localhost:" + port).auth(Http.uuid(loginB, "accessToken"));
		ResponseEntity<String> iso = apiB.getRaw("/api/v1/orders/" + orderId);
		assertThat(iso.getStatusCode().value()).isIn(403, 404);
		assertThat(apiB.getRaw("/api/v1/outlets/" + outletA + "/dashboard/summary?from=" + dashboardDate + "&to=" + dashboardDate).getStatusCode().value()).isEqualTo(403);

		var rotated = api.post("/api/v1/tables/" + tableId + "/rotate-qr", "{}");
		String newTok = Http.uuid(rotated, "token");
		ResponseEntity<String> old = new Http("http://localhost:" + port).postRaw("/api/v1/public/qr/" + qr + "/sessions", "{}", "old-sess");
		assertThat(old.getStatusCode().value()).isEqualTo(410);

		ResponseEntity<String> staleGuest = guest1.postRaw("/api/v1/public/qr/" + qr + "/rounds",
				"{\"items\":[{\"variantId\":\"" + variantId + "\",\"qty\":\"1\"}]}", "stale-round");
		assertThat(staleGuest.getStatusCode().value()).isEqualTo(410);
		assertThat(newTok).isNotEqualTo(qr);

		var voidOrder = api.post("/api/v1/outlets/" + outletA + "/orders",
				"{\"channel\":\"TAKEAWAY\",\"items\":[{\"variantId\":\"" + variantId + "\",\"qty\":\"1\"}]}",
				"void-1");
		api.post("/api/v1/orders/" + Http.uuid(voidOrder, "id") + "/cancel", "{}", "void-c");
		var bal2 = api.get("/api/v1/stock/balance?outletId=" + outletA + "&inventoryItemId=" + invId);
		assertThat(new java.math.BigDecimal(String.valueOf(bal2.get("qty")))).isEqualByComparingTo("900.0000");
		var oilBalance = api.get("/api/v1/stock/balance?outletId=" + outletA + "&inventoryItemId=" + oilId);
		assertThat(new java.math.BigDecimal(String.valueOf(oilBalance.get("qty")))).isEqualByComparingTo("90.0000");
	}

	private static String taxId(Map<String, Object> m, String k) {
		return String.valueOf(m.get(k));
	}
}
