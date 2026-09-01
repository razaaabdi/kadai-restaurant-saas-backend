CREATE TABLE invoice_amendments (
  id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), outlet_id UUID NOT NULL REFERENCES outlets(id),
  invoice_id UUID NOT NULL REFERENCES invoices(id), amendment_number INT NOT NULL, type TEXT NOT NULL,
  reason_code TEXT NOT NULL, reason_text TEXT NOT NULL, requested_by UUID NOT NULL REFERENCES users(id),
  approved_by UUID REFERENCES users(id), status TEXT NOT NULL DEFAULT 'REQUESTED', previous_total_paise BIGINT NOT NULL,
  new_total_paise BIGINT, approval_token_hash TEXT, approval_expires_at TIMESTAMPTZ, approved_at TIMESTAMPTZ,
  applied_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (invoice_id, amendment_number)
);
CREATE INDEX idx_invoice_amendments_tenant_created ON invoice_amendments(tenant_id, created_at DESC);
ALTER TABLE invoice_amendments ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE ON invoice_amendments TO restaurant_app;
CREATE POLICY p_invoice_amendments_iso ON invoice_amendments FOR ALL TO restaurant_app USING (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on') WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on');
