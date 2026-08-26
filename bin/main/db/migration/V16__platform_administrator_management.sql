ALTER TABLE platform_administrators ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE platform_administrators ADD COLUMN setup_token_hash TEXT UNIQUE;
ALTER TABLE platform_administrators ADD COLUMN setup_expires_at TIMESTAMPTZ;
ALTER TABLE platform_administrators ADD COLUMN last_login_at TIMESTAMPTZ;

CREATE TABLE platform_administrator_audit (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id UUID REFERENCES platform_administrators(id),
  administrator_id UUID NOT NULL REFERENCES platform_administrators(id),
  action TEXT NOT NULL,
  detail TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE ON platform_administrators TO restaurant_app;
GRANT SELECT, INSERT ON platform_administrator_audit TO restaurant_app;
