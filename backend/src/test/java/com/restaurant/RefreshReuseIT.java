package com.restaurant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshReuseIT extends AbstractIT {
	@Test
	void refreshReuseRevoked() {
		Http api = new Http("http://localhost:" + port);
		api.post("/api/v1/onboarding", """
				{"name":"Cafe R","slug":"cafe-r","email":"r@test.com","password":"secret12","ownerName":"R"}
				""");
		var login = api.post("/api/v1/auth/login", """
				{"email":"r@test.com","password":"secret12"}
				""");
		String refresh = Http.uuid(login, "refreshToken");
		var next = api.post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}");
		assertThat(next.get("accessToken")).isNotNull();
		var reuse = api.postRaw("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}", null);
		assertThat(reuse.getStatusCode().value()).isEqualTo(401);
	}
}
