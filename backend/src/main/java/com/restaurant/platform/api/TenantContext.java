package com.restaurant.platform.api;

public final class TenantContext {
	private static final ThreadLocal<TenantPrincipal> HOLDER = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> BOOTSTRAP = ThreadLocal.withInitial(() -> false);

	private TenantContext() {}

	public static void set(TenantPrincipal p) { HOLDER.set(p); }
	public static TenantPrincipal get() { return HOLDER.get(); }
	public static TenantPrincipal require() {
		TenantPrincipal p = HOLDER.get();
		if (p == null || p.tenantId() == null) {
			throw ApiException.unauthorized("Tenant context missing");
		}
		return p;
	}
	public static void bootstrap(boolean on) { BOOTSTRAP.set(on); }
	public static boolean bootstrap() { return Boolean.TRUE.equals(BOOTSTRAP.get()); }
	public static void clear() { HOLDER.remove(); BOOTSTRAP.remove(); }
}
