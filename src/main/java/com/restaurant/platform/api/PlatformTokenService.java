package com.restaurant.platform.api;

import java.util.UUID;

public interface PlatformTokenService {
	String platformToken(UUID administratorId);
	String refreshToken();
}
