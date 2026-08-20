-- App role cannot bypass RLS; Flyway runs as table owner (restaurant_owner).
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'restaurant_app') THEN
    CREATE ROLE restaurant_app LOGIN PASSWORD 'app_secret' NOSUPERUSER NOBYPASSRLS;
  END IF;
END
$$;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tenants (
  id UUID PRIMARY KEY,
  name TEXT NOT NULL,
  slug TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE brands (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  name TEXT NOT NULL
);

CREATE TABLE plans (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  code TEXT NOT NULL,
  inventory_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  multi_outlet BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE outlets (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  brand_id UUID NOT NULL REFERENCES brands(id),
  name TEXT NOT NULL,
  slug TEXT NOT NULL,
  timezone TEXT NOT NULL DEFAULT 'Asia/Kolkata',
  allow_negative_stock BOOLEAN NOT NULL DEFAULT TRUE,
  qr_ordering_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  qr_auto_confirm BOOLEAN NOT NULL DEFAULT TRUE,
  qr_guest_can_request_bill BOOLEAN NOT NULL DEFAULT TRUE,
  kot_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  takeaway_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  unlock_add_before_bill BOOLEAN NOT NULL DEFAULT TRUE,
  max_open_amount_paise BIGINT NOT NULL DEFAULT 5000000,
  service_charge_bps INT NOT NULL DEFAULT 0,
  packaging_charge_paise BIGINT NOT NULL DEFAULT 0,
  tax_inclusive BOOLEAN NOT NULL DEFAULT FALSE,
  rounding_mode TEXT NOT NULL DEFAULT 'HALF_UP',
  UNIQUE (tenant_id, slug)
);

CREATE TABLE users (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  email TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  name TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  UNIQUE (email)
);

CREATE TABLE roles (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  code TEXT NOT NULL,
  UNIQUE (tenant_id, code)
);

CREATE TABLE user_roles (
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  user_id UUID NOT NULL REFERENCES users(id),
  role_id UUID NOT NULL REFERENCES roles(id),
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_outlets (
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  user_id UUID NOT NULL REFERENCES users(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  PRIMARY KEY (user_id, outlet_id)
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  user_id UUID NOT NULL REFERENCES users(id),
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE password_reset_tokens (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  user_id UUID NOT NULL REFERENCES users(id),
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE areas (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  name TEXT NOT NULL
);

CREATE TABLE tables (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  area_id UUID NOT NULL REFERENCES areas(id),
  code TEXT NOT NULL,
  seats INT NOT NULL DEFAULT 4,
  status TEXT NOT NULL DEFAULT 'FREE',
  qr_locked BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, outlet_id, code)
);

CREATE TABLE qr_tokens (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  table_id UUID NOT NULL REFERENCES tables(id),
  token_hash TEXT NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE table_sessions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  table_id UUID NOT NULL REFERENCES tables(id),
  qr_token_id UUID NOT NULL REFERENCES qr_tokens(id),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  name TEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE tax_codes (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  code TEXT NOT NULL,
  rate_bps INT NOT NULL,
  UNIQUE (tenant_id, code)
);

CREATE TABLE items (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  category_id UUID NOT NULL REFERENCES categories(id),
  name TEXT NOT NULL,
  available_on_qr BOOLEAN NOT NULL DEFAULT TRUE,
  available_on_counter BOOLEAN NOT NULL DEFAULT TRUE,
  eighty_sixed BOOLEAN NOT NULL DEFAULT FALSE,
  tax_code_id UUID REFERENCES tax_codes(id),
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE variants (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  item_id UUID NOT NULL REFERENCES items(id),
  name TEXT NOT NULL,
  price_paise BIGINT NOT NULL
);

CREATE TABLE modifiers (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  name TEXT NOT NULL,
  extra_paise BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE inventory_items (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  name TEXT NOT NULL,
  unit TEXT NOT NULL DEFAULT 'g'
);

CREATE TABLE recipe_versions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  variant_id UUID NOT NULL REFERENCES variants(id),
  version_no INT NOT NULL,
  UNIQUE (variant_id, version_no)
);

CREATE TABLE recipe_lines (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  recipe_version_id UUID NOT NULL REFERENCES recipe_versions(id),
  inventory_item_id UUID NOT NULL REFERENCES inventory_items(id),
  qty NUMERIC(19,4) NOT NULL,
  modifier_id UUID REFERENCES modifiers(id)
);

CREATE TABLE stock_transactions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  inventory_item_id UUID NOT NULL REFERENCES inventory_items(id),
  type TEXT NOT NULL,
  qty NUMERIC(19,4) NOT NULL,
  order_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_balances (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  inventory_item_id UUID NOT NULL REFERENCES inventory_items(id),
  qty NUMERIC(19,4) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, outlet_id, inventory_item_id)
);

CREATE TABLE orders (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  table_id UUID REFERENCES tables(id),
  channel TEXT NOT NULL,
  status TEXT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  guest_frozen BOOLEAN NOT NULL DEFAULT FALSE,
  subtotal_paise BIGINT NOT NULL DEFAULT 0,
  discount_paise BIGINT NOT NULL DEFAULT 0,
  service_charge_paise BIGINT NOT NULL DEFAULT 0,
  packaging_paise BIGINT NOT NULL DEFAULT 0,
  tax_paise BIGINT NOT NULL DEFAULT 0,
  total_paise BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_one_open_table_order ON orders (tenant_id, outlet_id, table_id)
  WHERE table_id IS NOT NULL AND status NOT IN ('COMPLETED','CANCELLED','VOIDED');

CREATE TABLE order_rounds (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  order_id UUID NOT NULL REFERENCES orders(id),
  round_no INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (order_id, round_no)
);

CREATE TABLE order_lines (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  order_id UUID NOT NULL REFERENCES orders(id),
  round_id UUID NOT NULL REFERENCES order_rounds(id),
  variant_id UUID NOT NULL REFERENCES variants(id),
  name_snapshot TEXT NOT NULL,
  qty NUMERIC(19,4) NOT NULL,
  unit_paise BIGINT NOT NULL,
  line_paise BIGINT NOT NULL,
  recipe_version_id UUID REFERENCES recipe_versions(id)
);

CREATE TABLE order_line_modifiers (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  order_line_id UUID NOT NULL REFERENCES order_lines(id),
  modifier_id UUID NOT NULL REFERENCES modifiers(id),
  name_snapshot TEXT NOT NULL,
  extra_paise BIGINT NOT NULL
);

CREATE TABLE kots (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  order_id UUID NOT NULL REFERENCES orders(id),
  round_id UUID NOT NULL REFERENCES order_rounds(id),
  station TEXT NOT NULL DEFAULT 'HOT',
  kot_number INT NOT NULL,
  status TEXT NOT NULL DEFAULT 'NEW',
  reprint_of UUID REFERENCES kots(id)
);

CREATE TABLE kot_seq (
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  last_number INT NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, outlet_id)
);

CREATE TABLE invoices (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  order_id UUID NOT NULL REFERENCES orders(id),
  status TEXT NOT NULL,
  subtotal_paise BIGINT NOT NULL,
  discount_paise BIGINT NOT NULL,
  service_charge_paise BIGINT NOT NULL,
  packaging_paise BIGINT NOT NULL,
  tax_paise BIGINT NOT NULL,
  rounding_paise BIGINT NOT NULL DEFAULT 0,
  total_paise BIGINT NOT NULL,
  tax_inclusive BOOLEAN NOT NULL,
  rounding_mode TEXT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invoice_lines (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  invoice_id UUID NOT NULL REFERENCES invoices(id),
  name TEXT NOT NULL,
  qty NUMERIC(19,4) NOT NULL,
  unit_paise BIGINT NOT NULL,
  line_paise BIGINT NOT NULL,
  tax_paise BIGINT NOT NULL
);

CREATE TABLE payments (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  invoice_id UUID NOT NULL REFERENCES invoices(id),
  method TEXT NOT NULL,
  amount_paise BIGINT NOT NULL,
  change_paise BIGINT NOT NULL DEFAULT 0,
  status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_transactions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  payment_id UUID NOT NULL REFERENCES payments(id),
  status TEXT NOT NULL,
  amount_paise BIGINT NOT NULL,
  note TEXT
);

CREATE TABLE outlet_daily_sales (
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  business_date DATE NOT NULL,
  orders_count INT NOT NULL DEFAULT 0,
  gmv_paise BIGINT NOT NULL DEFAULT 0,
  discount_paise BIGINT NOT NULL DEFAULT 0,
  tax_paise BIGINT NOT NULL DEFAULT 0,
  refund_paise BIGINT NOT NULL DEFAULT 0,
  cash_paise BIGINT NOT NULL DEFAULT 0,
  upi_paise BIGINT NOT NULL DEFAULT 0,
  card_paise BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, outlet_id, business_date)
);

CREATE TABLE idempotency_keys (
  tenant_id UUID NOT NULL,
  key TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  status_code INT NOT NULL,
  response_body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, key)
);

CREATE TABLE outbox (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  type TEXT NOT NULL,
  payload TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_error TEXT
);

CREATE TABLE audit_log (
  id UUID PRIMARY KEY,
  tenant_id UUID,
  actor_id UUID,
  action TEXT NOT NULL,
  entity_type TEXT,
  entity_id UUID,
  detail TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE webhook_inbox (
  id UUID PRIMARY KEY,
  tenant_id UUID,
  provider TEXT NOT NULL,
  payload TEXT,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE config_entries (
  id UUID PRIMARY KEY,
  tenant_id UUID REFERENCES tenants(id),
  scope TEXT NOT NULL,
  scope_id UUID,
  key TEXT NOT NULL,
  value TEXT NOT NULL
);

CREATE INDEX idx_orders_tenant_outlet ON orders (tenant_id, outlet_id, created_at DESC);
CREATE INDEX idx_kots_outlet_status ON kots (tenant_id, outlet_id, status);
CREATE INDEX idx_outbox_poll ON outbox (status, next_attempt_at);
CREATE INDEX idx_stock_tx_item ON stock_transactions (tenant_id, outlet_id, inventory_item_id);

-- SECURITY DEFINER lookups before TenantContext exists (login, QR scan).
CREATE OR REPLACE FUNCTION lookup_user_by_email(p_email TEXT)
RETURNS TABLE(id UUID, tenant_id UUID, email TEXT, password_hash TEXT, name TEXT, status TEXT)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT u.id, u.tenant_id, u.email, u.password_hash, u.name, u.status
  FROM users u WHERE lower(u.email) = lower(p_email);
$$;

CREATE OR REPLACE FUNCTION lookup_qr_by_hash(p_hash TEXT)
RETURNS TABLE(
  token_id UUID, tenant_id UUID, table_id UUID, outlet_id UUID, active BOOLEAN,
  table_status TEXT, qr_locked BOOLEAN, qr_ordering_enabled BOOLEAN, outlet_name TEXT, table_code TEXT
)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT q.id, q.tenant_id, q.table_id, t.outlet_id, q.active,
         t.status, t.qr_locked, o.qr_ordering_enabled, o.name, t.code
  FROM qr_tokens q
  JOIN tables t ON t.id = q.table_id
  JOIN outlets o ON o.id = t.outlet_id
  WHERE q.token_hash = p_hash;
$$;

REVOKE ALL ON FUNCTION lookup_user_by_email(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION lookup_qr_by_hash(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION lookup_user_by_email(TEXT) TO restaurant_app;
GRANT EXECUTE ON FUNCTION lookup_qr_by_hash(TEXT) TO restaurant_app;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT tablename FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename NOT IN ('flyway_schema_history')
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', r.tablename);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE %I TO restaurant_app', r.tablename);
  END LOOP;
END
$$;

GRANT USAGE ON SCHEMA public TO restaurant_app;

-- Shared tables used before tenant GUC (idempotency still tenant-scoped).
CREATE POLICY p_tenants_all ON tenants FOR ALL TO restaurant_app
  USING (id::text = current_setting('app.current_tenant', true) OR current_setting('app.bootstrap', true) = 'on')
  WITH CHECK (id::text = current_setting('app.current_tenant', true) OR current_setting('app.bootstrap', true) = 'on');

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.relname AS tablename
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname <> 'tenants'
      AND EXISTS (
        SELECT 1 FROM pg_attribute a
        WHERE a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
      )
  LOOP
    EXECUTE format(
      'CREATE POLICY p_%s_iso ON %I FOR ALL TO restaurant_app USING (
         tenant_id::text = current_setting(''app.current_tenant'', true)
         OR current_setting(''app.bootstrap'', true) = ''on''
       ) WITH CHECK (
         tenant_id::text = current_setting(''app.current_tenant'', true)
         OR current_setting(''app.bootstrap'', true) = ''on''
       )', r.tablename, r.tablename);
  END LOOP;
END
$$;

-- webhook_inbox may have null tenant; allow app insert in bootstrap only is fine.
