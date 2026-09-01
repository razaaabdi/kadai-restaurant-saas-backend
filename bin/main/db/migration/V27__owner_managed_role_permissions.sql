ALTER TABLE roles
    ADD COLUMN permissions_customized BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN roles.permissions_customized IS
    'True after a tenant owner explicitly replaces this role permission set; default seeding must not restore removed permissions.';
