CREATE TABLE waiter_assignment_events (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  assignment_id UUID NOT NULL REFERENCES waiter_assignments(id),
  order_id UUID NOT NULL REFERENCES orders(id),
  table_id UUID NOT NULL REFERENCES tables(id),
  event_type TEXT NOT NULL,
  actor_user_id UUID REFERENCES users(id),
  previous_waiter_id UUID REFERENCES users(id),
  new_waiter_id UUID REFERENCES users(id),
  previous_status TEXT,
  new_status TEXT,
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE waiter_notifications ADD COLUMN assignment_id UUID REFERENCES waiter_assignments(id);
ALTER TABLE waiter_notifications ADD COLUMN read_at TIMESTAMPTZ;

CREATE INDEX idx_waiter_assignment_events_history ON waiter_assignment_events(outlet_id, assignment_id, created_at DESC);
CREATE INDEX idx_waiter_notifications_assignment ON waiter_notifications(outlet_id, assignment_id, created_at DESC);

ALTER TABLE waiter_assignment_events ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT ON waiter_assignment_events TO restaurant_app;
CREATE POLICY p_waiter_assignment_events_iso ON waiter_assignment_events FOR ALL TO restaurant_app
  USING (tenant_id::text=current_setting('app.current_tenant',true))
  WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true));
