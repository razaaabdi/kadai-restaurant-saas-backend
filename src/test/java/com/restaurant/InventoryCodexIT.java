package com.restaurant;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryCodexIT extends AbstractIT {
	@Test
	@SuppressWarnings("unchecked")
	void inventoryMasterLedgerWastageAndTenantIsolation() {
		Http api = new Http("http://localhost:" + port);
		Map<String, Object> a = api.post("/api/v1/onboarding", """
				{"name":"Inv A","slug":"inv-a","email":"inv-a@test.com","password":"secret12","ownerName":"A"}
				""");
		String outletA = Http.uuid(a, "outletId");
		Map<String, Object> loginA = api.post("/api/v1/auth/login", """
				{"email":"inv-a@test.com","password":"secret12"}
				""");
		api.auth(Http.uuid(loginA, "accessToken"));

		Map<String, Object> cat = api.post("/api/v1/inventory/categories", "{\"name\":\"Meat\",\"description\":\"Proteins\"}");
		String categoryId = Http.uuid(cat, "id");
		List<Map<String, Object>> locations = (List<Map<String, Object>>) api.get("/api/v1/inventory/stock-locations?outletId=" + outletA).get("list");
		assertThat(locations).extracting(row -> row.get("name")).contains("Main Store");

		Map<String, Object> chicken = api.post("/api/v1/inventory/items", """
				{"outletId":"%s","name":"Chicken","sku":"CHK-1","unit":"KILOGRAM","categoryId":"%s","openingQty":"10","unitCostPaise":30000,"reorderLevel":"4","minimumStock":"2"}
				""".formatted(outletA, categoryId));
		String itemId = Http.uuid(chicken, "id");
		assertThat(chicken.get("status")).isEqualTo("IN_STOCK");
		assertThat(chicken.get("currentStock").toString()).startsWith("10");
		assertThat(((Number) chicken.get("averageCostPaise")).longValue()).isEqualTo(30000);

		api.post("/api/v1/inventory/items", """
				{"outletId":"%s","name":"Paneer","sku":"PNR-1","unit":"KILOGRAM","openingQty":"1","reorderLevel":"5"}
				""".formatted(outletA));

		Map<String, Object> listed = api.get("/api/v1/inventory/items?outletId=" + outletA + "&page=0&size=25");
		assertThat((List<?>) listed.get("content")).hasSize(2);

		api.post("/api/v1/inventory/adjustments", """
				{"outletId":"%s","inventoryItemId":"%s","type":"ADJUSTMENT_IN","quantity":"5","reason":"Opening correction","unitCostPaise":33000}
				""".formatted(outletA, itemId));
		Map<String, Object> afterBuy = api.get("/api/v1/inventory/items/" + itemId);
		assertThat(afterBuy.get("currentStock").toString()).startsWith("15");
		assertThat(((Number) afterBuy.get("averageCostPaise")).longValue()).isEqualTo(31000);

		api.post("/api/v1/inventory/wastage", """
				{"outletId":"%s","inventoryItemId":"%s","quantity":"1","reason":"SPOILED","notes":"Fridge failure"}
				""".formatted(outletA, itemId));
		Map<String, Object> afterWaste = api.get("/api/v1/inventory/items/" + itemId);
		assertThat(afterWaste.get("currentStock").toString()).startsWith("14");

		Map<String, Object> movements = api.get("/api/v1/inventory/items/" + itemId + "/movements?page=0&size=25");
		assertThat((List<?>) movements.get("content")).isNotEmpty();

		Map<String, Object> dash = api.get("/api/v1/inventory/dashboard?outletId=" + outletA);
		assertThat(((Number) dash.get("lowStockItems")).intValue()).isGreaterThanOrEqualTo(1);
		assertThat(api.postRaw("/api/v1/inventory/items/" + itemId + "/deactivate", "{}", null).getStatusCode().is2xxSuccessful()).isTrue();

		Http other = new Http("http://localhost:" + port);
		other.post("/api/v1/onboarding", """
				{"name":"Inv B","slug":"inv-b","email":"inv-b@test.com","password":"secret12","ownerName":"B"}
				""");
		Map<String, Object> loginB = other.post("/api/v1/auth/login", """
				{"email":"inv-b@test.com","password":"secret12"}
				""");
		other.auth(Http.uuid(loginB, "accessToken"));
		int isolated = other.getRaw("/api/v1/inventory/items/" + itemId).getStatusCode().value();
		assertThat(isolated).isIn(403, 404);
	}
}
