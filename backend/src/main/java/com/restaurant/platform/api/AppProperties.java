package com.restaurant.platform.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
	private Jwt jwt = new Jwt();
	private String publicBase = "http://localhost:8080";
	private Outbox outbox = new Outbox();

	public Jwt getJwt() { return jwt; }
	public void setJwt(Jwt jwt) { this.jwt = jwt; }
	public String getPublicBase() { return publicBase; }
	public void setPublicBase(String publicBase) { this.publicBase = publicBase; }
	public Outbox getOutbox() { return outbox; }
	public void setOutbox(Outbox outbox) { this.outbox = outbox; }

	public static class Jwt {
		private String secret = "dev-only-change-me-32-bytes-min-secret";
		private long staffTtlSeconds = 3600;
		private long refreshTtlSeconds = 1_209_600;
		private long guestTtlSeconds = 7200;
		public String getSecret() { return secret; }
		public void setSecret(String secret) { this.secret = secret; }
		public long getStaffTtlSeconds() { return staffTtlSeconds; }
		public void setStaffTtlSeconds(long v) { this.staffTtlSeconds = v; }
		public long getRefreshTtlSeconds() { return refreshTtlSeconds; }
		public void setRefreshTtlSeconds(long v) { this.refreshTtlSeconds = v; }
		public long getGuestTtlSeconds() { return guestTtlSeconds; }
		public void setGuestTtlSeconds(long v) { this.guestTtlSeconds = v; }
	}

	public static class Outbox {
		private long pollMs = 2000;
		private int maxAttempts = 8;
		public long getPollMs() { return pollMs; }
		public void setPollMs(long pollMs) { this.pollMs = pollMs; }
		public int getMaxAttempts() { return maxAttempts; }
		public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
	}
}
