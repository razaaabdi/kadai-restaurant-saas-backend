-- One tenant-wide sequence avoids number collisions across a restaurant's outlets.
CREATE TABLE invoice_number_sequences (tenant_id UUID PRIMARY KEY REFERENCES tenants(id), last_number BIGINT NOT NULL DEFAULT 0);
ALTER TABLE invoices ADD COLUMN invoice_number TEXT;
CREATE UNIQUE INDEX ux_invoices_tenant_number ON invoices(tenant_id, invoice_number) WHERE invoice_number IS NOT NULL;
CREATE OR REPLACE FUNCTION assign_invoice_number() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE next_no BIGINT;
BEGIN
  IF NEW.invoice_number IS NOT NULL THEN RETURN NEW; END IF;
  INSERT INTO invoice_number_sequences(tenant_id,last_number) VALUES(NEW.tenant_id,1)
  ON CONFLICT(tenant_id) DO UPDATE SET last_number=invoice_number_sequences.last_number+1 RETURNING last_number INTO next_no;
  NEW.invoice_number := 'INV-' || lpad(next_no::text, 6, '0'); RETURN NEW;
END $$;
CREATE TRIGGER trg_assign_invoice_number BEFORE INSERT ON invoices FOR EACH ROW EXECUTE FUNCTION assign_invoice_number();
