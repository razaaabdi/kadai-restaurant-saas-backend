-- Legacy rows use their immutable UUID fragment, avoiding collisions with the numeric V25 sequence.
UPDATE invoices SET invoice_number='INV-LEGACY-' || upper(substr(replace(id::text,'-',''),1,12))
WHERE invoice_number IS NULL;

ALTER TABLE invoices ALTER COLUMN invoice_number SET NOT NULL;
ALTER TABLE invoice_number_sequences ENABLE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE ON invoice_number_sequences TO restaurant_app;
CREATE POLICY p_invoice_number_sequences_iso ON invoice_number_sequences FOR ALL TO restaurant_app
  USING (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on')
  WITH CHECK (tenant_id::text=current_setting('app.current_tenant',true) OR current_setting('app.bootstrap',true)='on');
