CREATE TABLE subscription_plans (
  id UUID PRIMARY KEY,
  code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  description TEXT,
  billing_cycle TEXT NOT NULL DEFAULT 'MONTHLY',
  price_paise BIGINT NOT NULL DEFAULT 0,
  currency TEXT NOT NULL DEFAULT 'INR',
  max_outlets INT NOT NULL,
  max_users INT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  features JSONB NOT NULL DEFAULT '[]'::jsonb,
  version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO subscription_plans(id,code,name,description,price_paise,max_outlets,max_users,features) VALUES
 ('10000000-0000-0000-0000-000000000001','STARTER','Starter','Single-outlet restaurant operations',199900,1,10,'["ORDER_MANAGEMENT","BILLING","REPORTING"]'),
 ('10000000-0000-0000-0000-000000000002','PRO','Pro','Full restaurant operations with inventory',499900,5,50,'["ORDER_MANAGEMENT","BILLING","REPORTING","INVENTORY","MULTI_OUTLET"]'),
 ('10000000-0000-0000-0000-000000000003','ENTERPRISE','Enterprise','Expanded multi-outlet operations',999900,100,500,'["ORDER_MANAGEMENT","BILLING","REPORTING","INVENTORY","MULTI_OUTLET"]')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE tenants
  ADD COLUMN IF NOT EXISTS legal_name TEXT,
  ADD COLUMN IF NOT EXISTS display_name TEXT,
  ADD COLUMN IF NOT EXISTS restaurant_type TEXT,
  ADD COLUMN IF NOT EXISTS primary_contact_name TEXT,
  ADD COLUMN IF NOT EXISTS primary_contact_email TEXT,
  ADD COLUMN IF NOT EXISTS primary_contact_phone TEXT,
  ADD COLUMN IF NOT EXISTS address TEXT,
  ADD COLUMN IF NOT EXISTS city TEXT,
  ADD COLUMN IF NOT EXISTS state TEXT,
  ADD COLUMN IF NOT EXISTS country TEXT,
  ADD COLUMN IF NOT EXISTS currency TEXT NOT NULL DEFAULT 'INR',
  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
UPDATE tenants SET legal_name=coalesce(legal_name,name),display_name=coalesce(display_name,name);

ALTER TABLE outlets
  ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN IF NOT EXISTS address TEXT,
  ADD COLUMN IF NOT EXISTS city TEXT,
  ADD COLUMN IF NOT EXISTS state TEXT,
  ADD COLUMN IF NOT EXISTS country TEXT,
  ADD COLUMN IF NOT EXISTS contact_number TEXT,
  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE subscriptions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id),
  plan_id UUID NOT NULL REFERENCES subscription_plans(id),
  status TEXT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  grace_period_end_date DATE,
  max_outlets_override INT,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (end_date >= start_date),
  CHECK (max_outlets_override IS NULL OR max_outlets_override > 0)
);

CREATE TABLE subscription_history (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  subscription_id UUID NOT NULL REFERENCES subscriptions(id),
  action TEXT NOT NULL,
  old_plan_id UUID REFERENCES subscription_plans(id),
  new_plan_id UUID REFERENCES subscription_plans(id),
  old_end_date DATE,
  new_end_date DATE,
  reason TEXT,
  performed_by UUID,
  performed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscription_history_tenant ON subscription_history(tenant_id,performed_at DESC);

ALTER TABLE subscription_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_history ENABLE ROW LEVEL SECURITY;
GRANT SELECT ON subscription_plans TO restaurant_app;
GRANT SELECT,INSERT,UPDATE,DELETE ON subscriptions,subscription_history TO restaurant_app;
CREATE POLICY p_subscription_plans_read ON subscription_plans FOR SELECT TO restaurant_app USING (true);
CREATE POLICY p_subscriptions_iso ON subscriptions FOR ALL TO restaurant_app
 USING (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on')
 WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on');
CREATE POLICY p_subscription_history_iso ON subscription_history FOR ALL TO restaurant_app
 USING (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on')
 WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on');

CREATE OR REPLACE FUNCTION platform_plans() RETURNS JSONB
LANGUAGE sql SECURITY DEFINER SET search_path=public AS $$
 SELECT coalesce(jsonb_agg(jsonb_build_object('id',id,'code',code,'name',name,'description',description,'billingCycle',billing_cycle,'pricePaise',price_paise,'currency',currency,'maxOutlets',max_outlets,'maxUsers',max_users,'active',active,'features',features,'version',version) ORDER BY name),'[]'::jsonb) FROM subscription_plans
$$;

CREATE OR REPLACE FUNCTION platform_dashboard() RETURNS JSONB
LANGUAGE sql SECURITY DEFINER SET search_path=public AS $$
 SELECT jsonb_build_object('totalRestaurants',(SELECT count(*) FROM tenants),'activeRestaurants',(SELECT count(*) FROM tenants WHERE status='ACTIVE'),'trialRestaurants',(SELECT count(*) FROM subscriptions WHERE status='TRIAL'),'expiringSoon',(SELECT count(*) FROM subscriptions WHERE status='EXPIRING_SOON' OR (status IN ('ACTIVE','TRIAL') AND end_date BETWEEN current_date AND current_date+30)),'expired',(SELECT count(*) FROM subscriptions WHERE status='EXPIRED' OR end_date<current_date),'suspended',(SELECT count(*) FROM tenants WHERE status='SUSPENDED'),'totalOutlets',(SELECT count(*) FROM outlets),'activeOutlets',(SELECT count(*) FROM outlets WHERE status='ACTIVE'))
$$;

CREATE OR REPLACE FUNCTION platform_restaurants(p_search TEXT,p_status TEXT) RETURNS JSONB
LANGUAGE sql SECURITY DEFINER SET search_path=public AS $$
 SELECT coalesce(jsonb_agg(row ORDER BY row->>'createdAt' DESC),'[]'::jsonb) FROM (
  SELECT jsonb_build_object('id',t.id,'code',t.slug,'legalName',coalesce(t.legal_name,t.name),'displayName',coalesce(t.display_name,t.name),'ownerName',coalesce(u.name,'—'),'ownerEmail',coalesce(u.email,'—'),'primaryContactPhone',t.primary_contact_phone,'city',t.city,'planName',coalesce(sp.name,'UNASSIGNED'),'outletCount',(SELECT count(*) FROM outlets o WHERE o.tenant_id=t.id),'userCount',(SELECT count(*) FROM users x WHERE x.tenant_id=t.id),'subscriptionStatus',coalesce(s.status,'EXPIRED'),'subscriptionStartDate',s.start_date,'subscriptionEndDate',s.end_date,'status',t.status,'createdAt',t.created_at,'version',t.version) row
  FROM tenants t LEFT JOIN subscriptions s ON s.tenant_id=t.id LEFT JOIN subscription_plans sp ON sp.id=s.plan_id
  LEFT JOIN LATERAL (SELECT x.name,x.email FROM users x JOIN user_roles ur ON ur.user_id=x.id JOIN roles r ON r.id=ur.role_id WHERE x.tenant_id=t.id AND r.code='OWNER' LIMIT 1) u ON true
  WHERE (coalesce(p_status,'')='' OR t.status=p_status) AND (coalesce(p_search,'')='' OR lower(coalesce(t.display_name,t.name)||' '||coalesce(t.legal_name,t.name)||' '||coalesce(u.email,'')) LIKE '%'||lower(p_search)||'%')
 ) q
$$;

CREATE OR REPLACE FUNCTION platform_restaurant(p_id UUID) RETURNS JSONB
LANGUAGE sql SECURITY DEFINER SET search_path=public AS $$
 SELECT jsonb_build_object('id',t.id,'code',t.slug,'legalName',coalesce(t.legal_name,t.name),'displayName',coalesce(t.display_name,t.name),'brandName',coalesce((SELECT name FROM brands WHERE tenant_id=t.id LIMIT 1),t.name),'ownerName',coalesce(u.name,'—'),'ownerEmail',coalesce(u.email,'—'),'primaryContactName',t.primary_contact_name,'primaryContactEmail',t.primary_contact_email,'primaryContactPhone',t.primary_contact_phone,'city',t.city,'planName',coalesce(sp.name,'UNASSIGNED'),'outletCount',(SELECT count(*) FROM outlets WHERE tenant_id=t.id),'userCount',(SELECT count(*) FROM users WHERE tenant_id=t.id),'subscriptionStatus',coalesce(s.status,'EXPIRED'),'subscriptionStartDate',s.start_date,'subscriptionEndDate',s.end_date,'status',t.status,'createdAt',t.created_at,'version',t.version,'subscriptionVersion',coalesce(s.version,0),'timezone',coalesce((SELECT timezone FROM outlets WHERE tenant_id=t.id LIMIT 1),'Asia/Kolkata'),'currency',t.currency,
 'outlets',coalesce((SELECT jsonb_agg(jsonb_build_object('id',o.id,'tenantId',o.tenant_id,'code',o.slug,'name',o.name,'city',o.city,'state',o.state,'timezone',o.timezone,'status',o.status,'active',o.status='ACTIVE','version',o.version) ORDER BY o.name) FROM outlets o WHERE o.tenant_id=t.id),'[]'::jsonb),
 'enabledFeatures',coalesce(sp.features,'[]'::jsonb),
 'subscriptionHistory',coalesce((SELECT jsonb_agg(jsonb_build_object('id',h.id,'action',h.action,'oldPlan',op.name,'newPlan',np.name,'oldEndDate',h.old_end_date,'newEndDate',h.new_end_date,'reason',h.reason,'performedBy',coalesce(pa.display_name,'system'),'performedAt',h.performed_at) ORDER BY h.performed_at DESC) FROM subscription_history h LEFT JOIN subscription_plans op ON op.id=h.old_plan_id LEFT JOIN subscription_plans np ON np.id=h.new_plan_id LEFT JOIN platform_administrators pa ON pa.id=h.performed_by WHERE h.tenant_id=t.id),'[]'::jsonb))
 FROM tenants t LEFT JOIN subscriptions s ON s.tenant_id=t.id LEFT JOIN subscription_plans sp ON sp.id=s.plan_id LEFT JOIN LATERAL (SELECT x.name,x.email FROM users x JOIN user_roles ur ON ur.user_id=x.id JOIN roles r ON r.id=ur.role_id WHERE x.tenant_id=t.id AND r.code='OWNER' LIMIT 1) u ON true WHERE t.id=p_id
$$;

GRANT EXECUTE ON FUNCTION platform_dashboard(),platform_plans(),platform_restaurants(TEXT,TEXT),platform_restaurant(UUID) TO restaurant_app;
