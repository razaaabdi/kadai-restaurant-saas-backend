ALTER TABLE items ADD COLUMN description VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE items ADD COLUMN image_url VARCHAR(2048);
ALTER TABLE tables ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tables DROP CONSTRAINT IF EXISTS tables_tenant_id_outlet_id_code_key;
CREATE UNIQUE INDEX ux_tables_active_code ON tables (tenant_id, outlet_id, lower(code)) WHERE deleted = FALSE;

CREATE INDEX idx_areas_outlet ON areas (tenant_id, outlet_id, name);
CREATE INDEX idx_tables_area ON tables (tenant_id, area_id, code);
CREATE INDEX idx_items_outlet_active ON items (tenant_id, outlet_id, category_id) WHERE deleted = FALSE;
CREATE INDEX idx_variants_item ON variants (tenant_id, item_id);

ALTER TABLE tables ADD CONSTRAINT ck_tables_seats CHECK (seats BETWEEN 1 AND 50);
ALTER TABLE tables ADD CONSTRAINT ck_tables_status CHECK (status IN ('FREE','OCCUPIED','RESERVED','BILL_REQUESTED','PAID_DIRTY'));
ALTER TABLE variants ADD CONSTRAINT ck_variants_price_positive CHECK (price_paise > 0);
