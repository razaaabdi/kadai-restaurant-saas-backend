-- Keep invoice snapshots immutable while allowing a pre-payment bill to be revised.
DROP TRIGGER IF EXISTS trg_one_invoice_per_order ON invoices;
CREATE OR REPLACE FUNCTION reject_duplicate_open_invoice_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.order_id::text, 71001));
  IF NEW.status = 'GENERATED' AND EXISTS (SELECT 1 FROM invoices WHERE order_id=NEW.order_id AND status='GENERATED' AND id<>NEW.id) THEN
    RAISE EXCEPTION 'generated invoice already exists for order' USING ERRCODE='23505';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_one_open_invoice_per_order BEFORE INSERT OR UPDATE OF order_id,status ON invoices
FOR EACH ROW EXECUTE FUNCTION reject_duplicate_open_invoice_order();
