-- Hot read paths used by waiter, order detail, billing, and payment summaries.
-- Keep tenant_id first so PostgreSQL row-level-security filtering remains index-friendly.
CREATE INDEX IF NOT EXISTS idx_kots_tenant_order
    ON kots (tenant_id, order_id);

CREATE INDEX IF NOT EXISTS idx_invoices_tenant_order_created
    ON invoices (tenant_id, order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payments_tenant_invoice_status
    ON payments (tenant_id, invoice_id, status);
