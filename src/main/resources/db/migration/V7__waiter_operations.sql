ALTER TABLE orders ADD COLUMN assigned_waiter_id UUID REFERENCES users(id);
ALTER TABLE orders ADD COLUMN guest_count INT NOT NULL DEFAULT 1;

ALTER TABLE order_lines ADD COLUMN fulfilment_status TEXT NOT NULL DEFAULT 'SENT_TO_KITCHEN';
ALTER TABLE order_lines ADD COLUMN notes TEXT;
ALTER TABLE order_lines ADD COLUMN picked_up_by UUID REFERENCES users(id);
ALTER TABLE order_lines ADD COLUMN picked_up_at TIMESTAMPTZ;
ALTER TABLE order_lines ADD COLUMN served_by UUID REFERENCES users(id);
ALTER TABLE order_lines ADD COLUMN served_at TIMESTAMPTZ;
ALTER TABLE order_lines ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE tables DROP CONSTRAINT ck_tables_status;
ALTER TABLE tables ADD CONSTRAINT ck_tables_status CHECK (status IN ('FREE','OCCUPIED','RESERVED','BILL_REQUESTED','PAID_DIRTY','CLEANING_REQUIRED','CLEANING','OUT_OF_SERVICE'));

CREATE TABLE waiter_notifications (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  recipient_user_id UUID REFERENCES users(id),
  event_type TEXT NOT NULL,
  order_id UUID NOT NULL REFERENCES orders(id),
  table_id UUID REFERENCES tables(id),
  kot_id UUID REFERENCES kots(id),
  related_item_ids TEXT,
  message TEXT NOT NULL,
  destination TEXT NOT NULL,
  acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
  acknowledged_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  dedupe_key TEXT NOT NULL,
  UNIQUE (tenant_id, dedupe_key)
);

CREATE INDEX idx_waiter_active_orders ON orders (tenant_id, outlet_id, assigned_waiter_id, created_at DESC)
  WHERE status NOT IN ('COMPLETED', 'CANCELLED', 'VOIDED');
CREATE INDEX idx_order_lines_fulfilment ON order_lines (tenant_id, order_id, fulfilment_status);
CREATE INDEX idx_waiter_notifications_recipient ON waiter_notifications (tenant_id, outlet_id, recipient_user_id, acknowledged, created_at DESC);

ALTER TABLE waiter_notifications ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE waiter_notifications TO restaurant_app;
CREATE POLICY p_waiter_notifications_iso ON waiter_notifications FOR ALL TO restaurant_app
  USING (tenant_id::text = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id::text = current_setting('app.current_tenant', true));
