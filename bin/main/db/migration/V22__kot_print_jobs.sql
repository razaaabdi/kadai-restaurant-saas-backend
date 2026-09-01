CREATE TABLE kot_print_jobs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  outlet_id UUID NOT NULL REFERENCES outlets(id),
  kot_id UUID NOT NULL REFERENCES kots(id),
  kind TEXT NOT NULL CHECK (kind IN ('INITIAL','REPRINT')),
  status TEXT NOT NULL CHECK (status IN ('PENDING','PRINTED','FAILED')),
  reason TEXT,
  attempt_count INT NOT NULL DEFAULT 0,
  last_error TEXT,
  printed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_kot_initial_print_job ON kot_print_jobs(kot_id) WHERE kind='INITIAL';
CREATE INDEX idx_kot_print_jobs_retry ON kot_print_jobs(status,created_at);
ALTER TABLE kot_print_jobs ENABLE ROW LEVEL SECURITY;
GRANT SELECT,INSERT,UPDATE,DELETE ON kot_print_jobs TO restaurant_app;
CREATE POLICY p_kot_print_jobs_iso ON kot_print_jobs FOR ALL TO restaurant_app
 USING (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on')
 WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on');
