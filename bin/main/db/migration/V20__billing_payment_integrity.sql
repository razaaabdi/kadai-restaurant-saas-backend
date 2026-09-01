-- Enforce new writes without requiring unknown legacy rows to pass validation at deployment time.
ALTER TABLE payments ADD CONSTRAINT ck_payments_method CHECK (method IN ('CASH','UPI','CARD')) NOT VALID;
ALTER TABLE payments ADD CONSTRAINT ck_payments_amount_positive CHECK (amount_paise > 0) NOT VALID;
ALTER TABLE payments ADD CONSTRAINT ck_payments_change_nonnegative CHECK (change_paise >= 0) NOT VALID;
ALTER TABLE payments ADD CONSTRAINT ck_payments_status CHECK (status IN ('PENDING','SUCCESS','FAILED','REFUNDED')) NOT VALID;
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_status CHECK (status IN ('GENERATED','PAID','VOID')) NOT VALID;

CREATE OR REPLACE FUNCTION reject_duplicate_invoice_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.order_id::text, 71001));
  IF EXISTS (SELECT 1 FROM invoices WHERE order_id=NEW.order_id AND id<>NEW.id) THEN
    RAISE EXCEPTION 'invoice already exists for order' USING ERRCODE='23505';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_one_invoice_per_order BEFORE INSERT OR UPDATE OF order_id ON invoices
FOR EACH ROW EXECUTE FUNCTION reject_duplicate_invoice_order();

CREATE OR REPLACE FUNCTION reject_duplicate_kot_round() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.round_id::text, 71002));
  IF EXISTS (SELECT 1 FROM kots WHERE round_id=NEW.round_id AND id<>NEW.id) THEN
    RAISE EXCEPTION 'KOT already exists for round' USING ERRCODE='23505';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_one_kot_per_round BEFORE INSERT OR UPDATE OF round_id ON kots
FOR EACH ROW EXECUTE FUNCTION reject_duplicate_kot_round();
