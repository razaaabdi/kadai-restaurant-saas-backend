package com.restaurant;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderModesIT extends AbstractIT {
    @Test
    @SuppressWarnings("unchecked")
    void supportsDineInAndTakeawayContexts() {
        Http api = new Http("http://localhost:" + port);
        Map<String, Object> onboard = api.post("/api/v1/onboarding",
                "{\"name\":\"Mode Cafe\",\"slug\":\"mode-cafe\",\"email\":\"modes@test.com\",\"password\":\"secret12\",\"ownerName\":\"Owner\"}");
        String outletId = Http.uuid(onboard, "outletId");
        Map<String, Object> login = api.post("/api/v1/auth/login",
                "{\"email\":\"modes@test.com\",\"password\":\"secret12\"}");
        api.auth(Http.uuid(login, "accessToken"));

        Map<String, Object> config = api.get("/api/v1/order-configuration?outletId=" + outletId);
        assertThat((Map<String, Object>) config.get("configuration"))
                .containsEntry("defaultDineInEntryMode", "ASK_EVERY_TIME");

        Map<String, Object> area = api.post("/api/v1/outlets/" + outletId + "/areas", "{\"name\":\"Dining\"}");
        Map<String, Object> table = api.post("/api/v1/areas/" + Http.uuid(area, "id") + "/tables",
                "{\"code\":\"T1\",\"seats\":4}");
        String tableId = Http.uuid(table, "tableId");
        Map<String, Object> dineIn = api.post("/api/v1/tables/" + tableId + "/orders",
                "{\"orderEntryMode\":\"WAITER_PAPER_COUNTER\"}", UUID.randomUUID().toString());
        Map<String, Object> same = api.post("/api/v1/tables/" + tableId + "/orders",
                "{\"orderEntryMode\":\"DIRECT_POS\"}", UUID.randomUUID().toString());
        assertThat(Http.uuid(same, "id")).isEqualTo(Http.uuid(dineIn, "id"));
        assertThat(dineIn).containsEntry("orderType", "DINE_IN")
                .containsEntry("orderEntryMode", "WAITER_PAPER_COUNTER");

        Map<String, Object> category = api.post("/api/v1/outlets/" + outletId + "/categories", "{\"name\":\"Quick bites\"}");
        Map<String, Object> item = api.post("/api/v1/outlets/" + outletId + "/items",
                "{\"categoryId\":\"" + Http.uuid(category, "id") + "\",\"name\":\"Roll\",\"pricePaise\":19900,\"available\":true}");
        String variantId = Http.uuid(item, "variantId");
        Map<String, Object> takeaway = api.post("/api/v1/orders/takeaway",
                "{\"outletId\":\"" + outletId + "\",\"customerName\":\"Raza\",\"customerPhone\":\"9876543210\"}",
                UUID.randomUUID().toString());
        String orderId = Http.uuid(takeaway, "id");
        assertThat(takeaway).containsEntry("orderType", "TAKEAWAY")
                .containsEntry("orderEntryMode", "DIRECT_POS")
                .containsEntry("tableId", null);
        assertThat(String.valueOf(takeaway.get("tokenNumber"))).startsWith("A-");

        api.post("/api/v1/orders/" + orderId + "/rounds",
                "{\"items\":[{\"variantId\":\"" + variantId + "\",\"quantity\":1}]}",
                UUID.randomUUID().toString());
        Map<String, Object> second = api.post("/api/v1/orders/" + orderId + "/rounds",
                "{\"items\":[{\"variantId\":\"" + variantId + "\",\"quantity\":2}]}",
                UUID.randomUUID().toString());
        assertThat(Http.uuid(second, "id")).isEqualTo(orderId);
        assertThat(((Number) second.get("rounds")).intValue()).isEqualTo(2);

        List<Map<String, Object>> active = (List<Map<String, Object>>) api
                .get("/api/v1/orders/takeaway/active?outletId=" + outletId).get("list");
        assertThat(active).extracting(row -> String.valueOf(row.get("id"))).contains(orderId);
    }
}
