-- Expand-only migration. Existing order rows are intentionally left unchanged.
ALTER TABLE orders ADD COLUMN order_number TEXT;
ALTER TABLE orders ADD COLUMN order_type TEXT;
ALTER TABLE orders ADD COLUMN order_entry_mode TEXT;
ALTER TABLE orders ADD COLUMN token_number TEXT;
ALTER TABLE orders ADD COLUMN customer_name TEXT;
ALTER TABLE orders ADD COLUMN customer_phone TEXT;
ALTER TABLE orders ADD COLUMN business_date DATE;
ALTER TABLE orders ADD COLUMN created_by UUID REFERENCES users(id);

CREATE UNIQUE INDEX ux_orders_number_v13
    ON orders (tenant_id, outlet_id, order_number)
    WHERE order_number IS NOT NULL;
CREATE UNIQUE INDEX ux_takeaway_token_business_date_v13
    ON orders (tenant_id, outlet_id, business_date, token_number)
    WHERE order_type = 'TAKEAWAY' AND token_number IS NOT NULL;
CREATE INDEX idx_orders_type_active_v13
    ON orders (tenant_id, outlet_id, order_type, created_at DESC)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED', 'VOIDED');

CREATE TABLE takeaway_token_seq (
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    outlet_id UUID NOT NULL REFERENCES outlets(id),
    business_date DATE NOT NULL,
    last_number INT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, outlet_id, business_date)
);

ALTER TABLE takeaway_token_seq ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE ON TABLE takeaway_token_seq TO restaurant_app;
CREATE POLICY p_takeaway_token_seq_iso ON takeaway_token_seq FOR ALL TO restaurant_app
    USING (tenant_id::text = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id::text = current_setting('app.current_tenant', true));
