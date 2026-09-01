CREATE TABLE inventory_order_consumptions (
  order_id UUID PRIMARY KEY REFERENCES orders(id),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  consumed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_consumptions_tenant ON inventory_order_consumptions(tenant_id,consumed_at DESC);
ALTER TABLE inventory_order_consumptions ENABLE ROW LEVEL SECURITY;
GRANT SELECT,INSERT,UPDATE,DELETE ON inventory_order_consumptions TO restaurant_app;
CREATE POLICY p_inventory_order_consumptions_iso ON inventory_order_consumptions FOR ALL TO restaurant_app
 USING (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on')
 WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on');
