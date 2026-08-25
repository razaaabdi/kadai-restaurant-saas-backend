CREATE TABLE inventory_categories (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_inventory_categories_name ON inventory_categories (tenant_id, lower(name));

CREATE TABLE stock_locations (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  name TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'MAIN_STORE',
  active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_stock_locations_name ON stock_locations (tenant_id, outlet_id, lower(name));

INSERT INTO stock_locations (id, tenant_id, outlet_id, name, type)
SELECT gen_random_uuid(), o.tenant_id, o.id, 'Main Store', 'MAIN_STORE'
FROM outlets o;

ALTER TABLE inventory_items ADD COLUMN sku TEXT;
ALTER TABLE inventory_items ADD COLUMN category_id UUID REFERENCES inventory_categories(id);
ALTER TABLE inventory_items ADD COLUMN minimum_stock NUMERIC(19,4) NOT NULL DEFAULT 0;
ALTER TABLE inventory_items ADD COLUMN reorder_level NUMERIC(19,4) NOT NULL DEFAULT 0;
ALTER TABLE inventory_items ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE inventory_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE inventory_items ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE inventory_items ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE UNIQUE INDEX ux_inventory_items_sku ON inventory_items (tenant_id, lower(sku)) WHERE sku IS NOT NULL AND sku <> '';

ALTER TABLE stock_transactions ADD COLUMN stock_location_id UUID REFERENCES stock_locations(id);
ALTER TABLE stock_transactions ADD COLUMN unit TEXT;
ALTER TABLE stock_transactions ADD COLUMN unit_cost_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stock_transactions ADD COLUMN total_cost_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stock_transactions ADD COLUMN reference_type TEXT;
ALTER TABLE stock_transactions ADD COLUMN reference_id UUID;
ALTER TABLE stock_transactions ADD COLUMN reason TEXT;
ALTER TABLE stock_transactions ADD COLUMN notes TEXT;
ALTER TABLE stock_transactions ADD COLUMN performed_by UUID REFERENCES users(id);
ALTER TABLE stock_transactions ADD COLUMN business_date DATE;

UPDATE stock_transactions t
SET stock_location_id = sl.id
FROM stock_locations sl
WHERE sl.outlet_id = t.outlet_id AND sl.name = 'Main Store';

ALTER TABLE stock_balances ADD COLUMN stock_location_id UUID REFERENCES stock_locations(id);
ALTER TABLE stock_balances ADD COLUMN average_cost_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stock_balances ADD COLUMN inventory_value_paise BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stock_balances ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

UPDATE stock_balances b
SET stock_location_id = sl.id
FROM stock_locations sl
WHERE sl.outlet_id = b.outlet_id AND sl.name = 'Main Store';

ALTER TABLE stock_balances ALTER COLUMN stock_location_id SET NOT NULL;
ALTER TABLE stock_balances DROP CONSTRAINT IF EXISTS stock_balances_tenant_id_outlet_id_inventory_item_id_key;
CREATE UNIQUE INDEX ux_stock_balance_location
  ON stock_balances (tenant_id, outlet_id, stock_location_id, inventory_item_id);

CREATE INDEX idx_stock_tx_outlet_item ON stock_transactions (tenant_id, outlet_id, inventory_item_id, created_at DESC);
CREATE INDEX idx_stock_tx_type_created ON stock_transactions (tenant_id, type, created_at DESC);
CREATE INDEX idx_stock_tx_outlet_created ON stock_transactions (tenant_id, outlet_id, created_at DESC);
CREATE INDEX idx_inventory_items_outlet ON inventory_items (tenant_id, outlet_id, active);
CREATE INDEX idx_stock_locations_outlet ON stock_locations (tenant_id, outlet_id);

ALTER TABLE inventory_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_locations ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE inventory_categories, stock_locations TO restaurant_app;
CREATE POLICY p_inventory_categories_iso ON inventory_categories FOR ALL TO restaurant_app
  USING (tenant_id::text = current_setting('app.current_tenant', true) OR current_setting('app.bootstrap', true) = 'on')
  WITH CHECK (tenant_id::text = current_setting('app.current_tenant', true) OR current_setting('app.bootstrap', true) = 'on');
CREATE POLICY p_stock_locations_iso ON stock_locations FOR ALL TO restaurant_app
  USING (tenant_id::text = current_setting('app.current_tenant', true) OR current_setting('app.bootstrap', true) = 'on')
  WITH CHECK (tenant_id::text = current_setting('app.current_tenant', true) OR current_setting('app.bootstrap', true) = 'on');
