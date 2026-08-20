package com.restaurant;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FloorMenuManagementIT extends AbstractIT {
    @Test
    @SuppressWarnings("unchecked")
    void validatesCrudPreservesHistoryAndReusesOneActiveOrderPerTable() {
        Http api = new Http("http://localhost:" + port);
        Map<String, Object> onboard = api.post("/api/v1/onboarding", """
                {"name":"Managed Cafe","slug":"managed-cafe","email":"managed@test.com","password":"secret12","ownerName":"Owner"}
                """);
        String outletId = Http.uuid(onboard, "outletId");
        Map<String, Object> login = api.post("/api/v1/auth/login", """
                {"email":"managed@test.com","password":"secret12"}
                """);
        api.auth(Http.uuid(login, "accessToken"));

        Map<String, Object> category = api.post("/api/v1/outlets/" + outletId + "/categories", "{\"name\":\"Mains\"}");
        String categoryId = Http.uuid(category, "id");
        Map<String, Object> item = api.post("/api/v1/outlets/" + outletId + "/items", """
                {"categoryId":"%s","name":"Paneer Bowl","description":"House paneer and rice","image":"https://example.com/paneer.jpg","pricePaise":25000,"available":false}
                """.formatted(categoryId));
        String itemId = Http.uuid(item, "itemId");
        String variantId = Http.uuid(item, "variantId");
        List<Map<String, Object>> menu = (List<Map<String, Object>>) api.get("/api/v1/outlets/" + outletId + "/menu").get("list");
        assertThat(menu).singleElement().satisfies(row -> {
            assertThat(row.get("description")).isEqualTo("House paneer and rice");
            assertThat(row.get("available")).isEqualTo(false);
            assertThat(row.get("category")).isEqualTo("Mains");
        });
        assertThat(api.putRaw("/api/v1/items/" + itemId,
                "{\"categoryId\":\"" + categoryId + "\",\"name\":\"Paneer Bowl\",\"description\":\"House paneer and rice\",\"image\":\"https://example.com/paneer.jpg\",\"pricePaise\":25000,\"available\":true}", 0)
                .getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> area = api.post("/api/v1/outlets/" + outletId + "/areas", "{\"name\":\"Main dining\"}");
        String areaId = Http.uuid(area, "id");
        Map<String, Object> table = api.post("/api/v1/areas/" + areaId + "/tables", "{\"code\":\"T1\",\"seats\":4}");
        String tableId = Http.uuid(table, "tableId");
        assertThat(api.postRaw("/api/v1/areas/" + areaId + "/tables", "{\"code\":\" t1 \",\"seats\":4}", null).getStatusCode().value()).isEqualTo(409);
        assertThat(api.postRaw("/api/v1/areas/" + areaId + "/tables", "{\"code\":\"T2\",\"seats\":0}", null).getStatusCode().value()).isEqualTo(400);

        Map<String, Object> order = api.post("/api/v1/outlets/" + outletId + "/orders",
                "{\"channel\":\"COUNTER_DINE_IN\",\"tableId\":\"" + tableId + "\",\"items\":[{\"variantId\":\"" + variantId + "\",\"qty\":\"1\"}]}", "create-order");
        String orderId = Http.uuid(order, "id");
        Map<String, Object> active = api.get("/api/v1/outlets/" + outletId + "/tables/" + tableId + "/active-order");
        assertThat(Http.uuid(active, "id")).isEqualTo(orderId);
        Map<String, Object> round = api.post("/api/v1/orders/" + orderId + "/rounds",
                "{\"items\":[{\"variantId\":\"" + variantId + "\",\"qty\":\"2\"}]}", UUID.randomUUID().toString());
        assertThat(Http.uuid(round, "id")).isEqualTo(orderId);
        assertThat(((Number) round.get("rounds")).intValue()).isEqualTo(2);
        assertThat(api.deleteRaw("/api/v1/tables/" + tableId, 0).getStatusCode().value()).isEqualTo(409);
    }
}
