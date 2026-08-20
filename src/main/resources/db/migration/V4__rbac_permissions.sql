ALTER TABLE users ADD COLUMN employee_code TEXT;
CREATE UNIQUE INDEX ux_users_tenant_employee_code ON users (tenant_id, upper(employee_code)) WHERE employee_code IS NOT NULL;

DROP FUNCTION lookup_user_by_email(TEXT);
CREATE FUNCTION lookup_user_by_email(p_email TEXT)
RETURNS TABLE(id UUID, tenant_id UUID, email TEXT, password_hash TEXT, name TEXT, status TEXT, employee_code TEXT)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT u.id, u.tenant_id, u.email, u.password_hash, u.name, u.status, u.employee_code
  FROM users u WHERE lower(u.email) = lower(p_email);
$$;
REVOKE ALL ON FUNCTION lookup_user_by_email(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION lookup_user_by_email(TEXT) TO restaurant_app;

CREATE TABLE permissions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  code TEXT NOT NULL,
  description TEXT NOT NULL,
  category TEXT NOT NULL,
  UNIQUE (tenant_id, code)
);

CREATE TABLE role_permissions (
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  role_id UUID NOT NULL REFERENCES roles(id),
  permission_id UUID NOT NULL REFERENCES permissions(id),
  PRIMARY KEY (role_id, permission_id)
);

INSERT INTO roles (id, tenant_id, code)
SELECT gen_random_uuid(), t.id, r.code FROM tenants t
CROSS JOIN (VALUES ('SHIFT_MANAGER'), ('INVENTORY_MANAGER')) r(code)
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO permissions (id, tenant_id, code, description, category)
SELECT gen_random_uuid(), t.id, p.code, p.description, p.category FROM tenants t
CROSS JOIN (VALUES
 ('DASHBOARD_VIEW','View operational dashboard','OPERATIONS'),
 ('ORDER_VIEW','View orders','ORDERS'), ('ORDER_CREATE','Create orders','ORDERS'), ('ORDER_CANCEL','Cancel orders','ORDERS'),
 ('BILL_CREATE','Create bills','BILLING'), ('PAYMENT_RECORD','Record payments','BILLING'), ('DISCOUNT_APPLY','Apply permitted discounts','BILLING'),
 ('KOT_VIEW','View kitchen tickets','KITCHEN'), ('KOT_UPDATE','Update kitchen tickets','KITCHEN'),
 ('MENU_VIEW','View menu administration','MENU'), ('MENU_EDIT','Edit menu','MENU'), ('PRICE_EDIT','Edit prices','MENU'),
 ('FLOOR_VIEW','View floor and tables','FLOOR'), ('FLOOR_EDIT','Configure floor and tables','FLOOR'),
 ('STOCK_VIEW','View stock','INVENTORY'), ('STOCK_EDIT','Manage stock and recipes','INVENTORY'), ('STOCK_ADJUST','Adjust stock','INVENTORY'),
 ('REPORT_OUTLET_VIEW','View outlet reports','REPORTS'), ('REPORT_ALL_VIEW','View tenant-wide reports','REPORTS'),
 ('USER_VIEW','View staff and access','ADMIN'), ('USER_MANAGE','Create and assign staff','ADMIN'),
 ('BRANDING_OUTLET_EDIT','Edit outlet branding','ADMIN'), ('BRANDING_TENANT_EDIT','Edit tenant branding','ADMIN'),
 ('AUDIT_VIEW','View audit history','ADMIN'), ('SETTINGS_VIEW','View settings','ADMIN')
) p(code, description, category)
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id FROM roles r JOIN permissions p ON p.tenant_id=r.tenant_id
WHERE r.code='OWNER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id FROM roles r JOIN permissions p ON p.tenant_id=r.tenant_id
WHERE (r.code='MANAGER' AND p.code NOT IN ('BRANDING_TENANT_EDIT','REPORT_ALL_VIEW'))
   OR (r.code='SHIFT_MANAGER' AND p.code IN ('DASHBOARD_VIEW','ORDER_VIEW','ORDER_CREATE','ORDER_CANCEL','BILL_CREATE','PAYMENT_RECORD','DISCOUNT_APPLY','KOT_VIEW','KOT_UPDATE','MENU_VIEW','FLOOR_VIEW','STOCK_VIEW','REPORT_OUTLET_VIEW'))
   OR (r.code='CASHIER' AND p.code IN ('ORDER_VIEW','ORDER_CREATE','BILL_CREATE','PAYMENT_RECORD','DISCOUNT_APPLY','MENU_VIEW','FLOOR_VIEW'))
   OR (r.code='WAITER' AND p.code IN ('ORDER_VIEW','ORDER_CREATE','MENU_VIEW','FLOOR_VIEW'))
   OR (r.code='KITCHEN' AND p.code IN ('KOT_VIEW','KOT_UPDATE'))
   OR (r.code='INVENTORY_MANAGER' AND p.code IN ('DASHBOARD_VIEW','MENU_VIEW','STOCK_VIEW','STOCK_EDIT','STOCK_ADJUST','REPORT_OUTLET_VIEW'))
ON CONFLICT DO NOTHING;

ALTER TABLE permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permissions ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE, DELETE ON permissions, role_permissions TO restaurant_app;
CREATE POLICY p_permissions_iso ON permissions FOR ALL TO restaurant_app
  USING (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on')
  WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on');
CREATE POLICY p_role_permissions_iso ON role_permissions FOR ALL TO restaurant_app
  USING (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on')
  WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on');
