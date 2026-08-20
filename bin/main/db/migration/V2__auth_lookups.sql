CREATE OR REPLACE FUNCTION lookup_refresh_by_hash(p_hash TEXT)
RETURNS TABLE(id UUID, tenant_id UUID, user_id UUID, token_hash TEXT, expires_at TIMESTAMPTZ, revoked BOOLEAN)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT r.id, r.tenant_id, r.user_id, r.token_hash, r.expires_at, r.revoked
  FROM refresh_tokens r WHERE r.token_hash = p_hash;
$$;

CREATE OR REPLACE FUNCTION lookup_reset_by_hash(p_hash TEXT)
RETURNS TABLE(id UUID, tenant_id UUID, user_id UUID, token_hash TEXT, expires_at TIMESTAMPTZ, used BOOLEAN)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT p.id, p.tenant_id, p.user_id, p.token_hash, p.expires_at, p.used
  FROM password_reset_tokens p WHERE p.token_hash = p_hash;
$$;

GRANT EXECUTE ON FUNCTION lookup_refresh_by_hash(TEXT) TO restaurant_app;
GRANT EXECUTE ON FUNCTION lookup_reset_by_hash(TEXT) TO restaurant_app;
