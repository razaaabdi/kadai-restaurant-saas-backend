ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE user_approval_limits (
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  user_id UUID PRIMARY KEY REFERENCES users(id),
  discount_bps INT NOT NULL DEFAULT 0 CHECK (discount_bps BETWEEN 0 AND 10000),
  refund_paise BIGINT NOT NULL DEFAULT 0 CHECK (refund_paise >= 0),
  void_paise BIGINT NOT NULL DEFAULT 0 CHECK (void_paise >= 0),
  stock_adjustment_paise BIGINT NOT NULL DEFAULT 0 CHECK (stock_adjustment_paise >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by UUID
);

ALTER TABLE user_approval_limits ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_approval_limits TO restaurant_app;
CREATE POLICY p_user_approval_limits_iso ON user_approval_limits FOR ALL TO restaurant_app
  USING (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on')
  WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on');

DROP FUNCTION lookup_user_by_email(TEXT);
CREATE FUNCTION lookup_user_by_email(p_email TEXT)
RETURNS TABLE(id UUID, tenant_id UUID, email TEXT, password_hash TEXT, name TEXT, status TEXT, employee_code TEXT, version BIGINT)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT u.id, u.tenant_id, u.email, u.password_hash, u.name, u.status, u.employee_code, u.version
  FROM users u WHERE lower(u.email)=lower(p_email);
$$;
REVOKE ALL ON FUNCTION lookup_user_by_email(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION lookup_user_by_email(TEXT) TO restaurant_app;
