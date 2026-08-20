ALTER TABLE config_entries
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN updated_by UUID;

CREATE UNIQUE INDEX ux_config_entries_scope_key
  ON config_entries (tenant_id, scope, COALESCE(scope_id, '00000000-0000-0000-0000-000000000000'::uuid), key);

CREATE INDEX idx_config_entries_effective
  ON config_entries (tenant_id, scope, scope_id, key);

ALTER TABLE config_entries
  ADD CONSTRAINT ck_config_entries_scope CHECK (
    (scope = 'TENANT' AND scope_id IS NULL)
    OR (scope = 'OUTLET' AND scope_id IS NOT NULL)
  );
