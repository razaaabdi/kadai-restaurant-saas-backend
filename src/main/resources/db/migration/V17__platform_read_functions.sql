CREATE OR REPLACE FUNCTION platform_dashboard() RETURNS JSONB
LANGUAGE sql SECURITY DEFINER SET search_path=public AS $$
  SELECT jsonb_build_object('totalRestaurants',count(*),'activeRestaurants',count(*) FILTER(WHERE status='ACTIVE'),'trialRestaurants',0,'expiringSoon',0,'expired',0,'suspended',count(*) FILTER(WHERE status='SUSPENDED'),'totalOutlets',(SELECT count(*) FROM outlets),'activeOutlets',(SELECT count(*) FROM outlets)) FROM tenants
$$;

CREATE OR REPLACE FUNCTION platform_plans() RETURNS JSONB
LANGUAGE sql SECURITY DEFINER SET search_path=public AS $$
  SELECT coalesce(jsonb_agg(x ORDER BY x->>'name'),'[]'::jsonb) FROM (SELECT DISTINCT ON(code) jsonb_build_object('id',id,'code',code,'name',replace(code,'_',' '),'description','Current tenant plan','billingCycle','MONTHLY','pricePaise',0,'currency','INR','maxOutlets',CASE WHEN multi_outlet THEN 100 ELSE 1 END,'maxUsers',100,'active',true,'features',to_jsonb(array_remove(ARRAY[CASE WHEN inventory_enabled THEN 'INVENTORY' END,CASE WHEN multi_outlet THEN 'MULTI_OUTLET' END,'ORDER_MANAGEMENT','BILLING','REPORTING'],NULL)),'version',0) x FROM plans ORDER BY code,id) q
$$;

CREATE OR REPLACE FUNCTION platform_restaurants(p_search TEXT,p_status TEXT) RETURNS JSONB
LANGUAGE sql SECURITY DEFINER SET search_path=public AS $$
  SELECT coalesce(jsonb_agg(row ORDER BY row->>'createdAt' DESC),'[]'::jsonb) FROM (SELECT jsonb_build_object('id',t.id,'code',t.slug,'legalName',t.name,'displayName',t.name,'ownerName',coalesce((SELECT u.name FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.tenant_id=t.id AND r.code='OWNER' LIMIT 1),'—'),'ownerEmail',coalesce((SELECT u.email FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.tenant_id=t.id AND r.code='OWNER' LIMIT 1),'—'),'planName',coalesce((SELECT code FROM plans WHERE tenant_id=t.id LIMIT 1),'UNASSIGNED'),'outletCount',(SELECT count(*) FROM outlets WHERE tenant_id=t.id),'userCount',(SELECT count(*) FROM users WHERE tenant_id=t.id),'subscriptionStatus',CASE WHEN t.status='ACTIVE' THEN 'ACTIVE' ELSE 'SUSPENDED' END,'status',t.status,'createdAt',t.created_at,'version',0) row FROM tenants t WHERE (coalesce(p_status,'')='' OR t.status=p_status) AND (coalesce(p_search,'')='' OR lower(t.name||' '||t.slug||' '||coalesce((SELECT string_agg(u.email||' '||u.name,' ') FROM users u WHERE u.tenant_id=t.id),'')) LIKE '%'||lower(p_search)||'%')) q
$$;

CREATE OR REPLACE FUNCTION platform_restaurant(p_id UUID) RETURNS JSONB
LANGUAGE sql SECURITY DEFINER SET search_path=public AS $$
  SELECT jsonb_build_object('id',t.id,'code',t.slug,'legalName',t.name,'displayName',t.name,'brandName',coalesce((SELECT name FROM brands WHERE tenant_id=t.id LIMIT 1),t.name),'ownerName',coalesce((SELECT u.name FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.tenant_id=t.id AND r.code='OWNER' LIMIT 1),'—'),'ownerEmail',coalesce((SELECT u.email FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.tenant_id=t.id AND r.code='OWNER' LIMIT 1),'—'),'primaryContactName',coalesce((SELECT u.name FROM users u WHERE u.tenant_id=t.id LIMIT 1),'—'),'primaryContactEmail',coalesce((SELECT u.email FROM users u WHERE u.tenant_id=t.id LIMIT 1),'—'),'planName',coalesce((SELECT code FROM plans WHERE tenant_id=t.id LIMIT 1),'UNASSIGNED'),'outletCount',(SELECT count(*) FROM outlets WHERE tenant_id=t.id),'userCount',(SELECT count(*) FROM users WHERE tenant_id=t.id),'subscriptionStatus',CASE WHEN t.status='ACTIVE' THEN 'ACTIVE' ELSE 'SUSPENDED' END,'status',t.status,'createdAt',t.created_at,'version',0,'subscriptionVersion',0,'timezone',coalesce((SELECT timezone FROM outlets WHERE tenant_id=t.id LIMIT 1),'Asia/Kolkata'),'currency','INR','outlets',coalesce((SELECT jsonb_agg(jsonb_build_object('id',o.id,'tenantId',o.tenant_id,'code',o.slug,'name',o.name,'timezone',o.timezone,'status','ACTIVE','active',true,'version',0) ORDER BY o.name) FROM outlets o WHERE o.tenant_id=t.id),'[]'::jsonb),'enabledFeatures',to_jsonb(array_remove(ARRAY[CASE WHEN coalesce((SELECT inventory_enabled FROM plans WHERE tenant_id=t.id LIMIT 1),false) THEN 'INVENTORY' END,CASE WHEN coalesce((SELECT multi_outlet FROM plans WHERE tenant_id=t.id LIMIT 1),false) THEN 'MULTI_OUTLET' END,'ORDER_MANAGEMENT','BILLING','REPORTING'],NULL)),'subscriptionHistory','[]'::jsonb) FROM tenants t WHERE t.id=p_id
$$;

CREATE OR REPLACE FUNCTION platform_audits(p_tenant UUID,p_search TEXT) RETURNS JSONB
LANGUAGE sql SECURITY DEFINER SET search_path=public AS $$
  SELECT coalesce(jsonb_agg(jsonb_build_object('id',id,'action',action,'superAdminUserId',coalesce(actor_id::text,'system'),'tenantId',tenant_id,'reason',detail,'timestamp',created_at) ORDER BY created_at DESC),'[]'::jsonb) FROM (SELECT * FROM audit_log WHERE (p_tenant IS NULL OR tenant_id=p_tenant) AND (coalesce(p_search,'')='' OR lower(action||' '||coalesce(detail,'')) LIKE '%'||lower(p_search)||'%') ORDER BY created_at DESC LIMIT 250) q
$$;

REVOKE ALL ON FUNCTION platform_dashboard() FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_plans() FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_restaurants(TEXT,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_restaurant(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION platform_audits(UUID,TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform_dashboard(),platform_plans(),platform_restaurants(TEXT,TEXT),platform_restaurant(UUID),platform_audits(UUID,TEXT) TO restaurant_app;
