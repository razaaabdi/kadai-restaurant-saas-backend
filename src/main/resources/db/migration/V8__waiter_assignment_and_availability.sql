CREATE TABLE waiter_work_profiles (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  waiter_id UUID NOT NULL REFERENCES users(id),
  manual_status TEXT NOT NULL DEFAULT 'ONLINE' CHECK (manual_status IN ('ONLINE','ON_BREAK','OFFLINE')),
  capacity INT NOT NULL DEFAULT 5 CHECK (capacity BETWEEN 1 AND 50),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (outlet_id, waiter_id)
);

CREATE TABLE waiter_assignments (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  table_id UUID NOT NULL REFERENCES tables(id),
  order_id UUID NOT NULL REFERENCES orders(id),
  waiter_id UUID NOT NULL REFERENCES users(id),
  status TEXT NOT NULL CHECK (status IN ('PENDING_ACCEPTANCE','ASSIGNED','TRANSFER_REQUESTED','TRANSFERRED','COMPLETED','CANCELLED')),
  assigned_by UUID REFERENCES users(id),
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  accepted_at TIMESTAMPTZ,
  released_at TIMESTAMPTZ,
  transfer_to_waiter_id UUID REFERENCES users(id),
  transfer_requested_by UUID REFERENCES users(id),
  transfer_requested_at TIMESTAMPTZ,
  transfer_reason TEXT,
  previous_assignment_id UUID REFERENCES waiter_assignments(id),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_waiter_primary_active_order ON waiter_assignments(order_id)
  WHERE status IN ('PENDING_ACCEPTANCE','ASSIGNED','TRANSFER_REQUESTED');
CREATE INDEX idx_waiter_assignments_workload ON waiter_assignments(outlet_id, waiter_id, status);
CREATE INDEX idx_waiter_assignments_table_history ON waiter_assignments(table_id, assigned_at DESC);

ALTER TABLE waiter_work_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE waiter_assignments ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE, DELETE ON waiter_work_profiles, waiter_assignments TO restaurant_app;
CREATE POLICY p_waiter_profiles_iso ON waiter_work_profiles FOR ALL TO restaurant_app
  USING (tenant_id::text=current_setting('app.current_tenant',true))
  WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true));
CREATE POLICY p_waiter_assignments_iso ON waiter_assignments FOR ALL TO restaurant_app
  USING (tenant_id::text=current_setting('app.current_tenant',true))
  WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true));

INSERT INTO waiter_work_profiles (id, tenant_id, outlet_id, waiter_id, manual_status, capacity)
SELECT gen_random_uuid(), uo.tenant_id, uo.outlet_id, uo.user_id, 'ONLINE', 5
FROM user_outlets uo JOIN users u ON u.id=uo.user_id
WHERE u.status='ACTIVE'
ON CONFLICT (outlet_id, waiter_id) DO NOTHING;

INSERT INTO waiter_assignments (id, tenant_id, outlet_id, table_id, order_id, waiter_id, status, assigned_by, accepted_at)
SELECT gen_random_uuid(), o.tenant_id, o.outlet_id, o.table_id, o.id, o.assigned_waiter_id, 'ASSIGNED', o.assigned_waiter_id, now()
FROM orders o
WHERE o.table_id IS NOT NULL AND o.assigned_waiter_id IS NOT NULL
  AND o.status NOT IN ('COMPLETED','CANCELLED','VOIDED')
ON CONFLICT DO NOTHING;
