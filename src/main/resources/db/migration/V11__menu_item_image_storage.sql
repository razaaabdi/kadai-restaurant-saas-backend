create table if not exists menu_item_images (
    id uuid primary key,
    tenant_id uuid not null,
    outlet_id uuid not null references outlets(id),
    content_type varchar(32) not null,
    content bytea not null,
    size_bytes bigint not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_menu_item_images_tenant_outlet
    on menu_item_images(tenant_id, outlet_id, created_at desc);
