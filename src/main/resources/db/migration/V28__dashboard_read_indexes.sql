CREATE INDEX IF NOT EXISTS idx_orders_dashboard_range ON orders (tenant_id, outlet_id, business_date, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_dashboard_time ON payments (tenant_id, created_at DESC) WHERE status = 'SUCCESS';
CREATE INDEX IF NOT EXISTS idx_invoices_dashboard_outlet ON invoices (tenant_id, outlet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_invoice_lines_dashboard_invoice ON invoice_lines (tenant_id, invoice_id);
