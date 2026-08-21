-- Keep invoice generation separate from payment completion.
UPDATE invoices SET status='GENERATED' WHERE status IN ('OPEN','PAID');

-- Repair only billed, unpaid active table orders with a verified invoice.
INSERT INTO audit_log(id,tenant_id,action,entity_type,entity_id,detail)
SELECT gen_random_uuid(),o.tenant_id,'BILLING_TABLE_RECONCILED','TABLE',t.id,
       'BILL_REQUESTED -> OCCUPIED; verified billed order='||o.id
FROM orders o
JOIN invoices i ON i.order_id=o.id
JOIN tables t ON t.id=o.table_id
WHERE o.status='BILLED' AND i.status='GENERATED' AND t.status='BILL_REQUESTED'
AND NOT EXISTS(
  SELECT 1 FROM payments p WHERE p.invoice_id=i.id AND p.status='SUCCESS'
  GROUP BY p.invoice_id HAVING sum(p.amount_paise)>=i.total_paise
);

UPDATE tables t SET status='OCCUPIED',version=t.version+1
FROM orders o JOIN invoices i ON i.order_id=o.id
WHERE t.id=o.table_id AND o.status='BILLED' AND i.status='GENERATED' AND t.status='BILL_REQUESTED'
AND NOT EXISTS(
  SELECT 1 FROM payments p WHERE p.invoice_id=i.id AND p.status='SUCCESS'
  GROUP BY p.invoice_id HAVING sum(p.amount_paise)>=i.total_paise
);

-- Preserve invalid assignment history, release active rows, audit the correction,
-- and leave the active order explicitly unassigned for a manager to reassign.
INSERT INTO audit_log(id,tenant_id,action,entity_type,entity_id,detail)
SELECT gen_random_uuid(),o.tenant_id,'INVALID_WAITER_ASSIGNMENT_REPAIRED','ORDER',o.id,
       'Removed ineligible assigned_waiter_id='||o.assigned_waiter_id
FROM orders o
WHERE o.assigned_waiter_id IS NOT NULL
AND NOT EXISTS(
  SELECT 1 FROM users u
  JOIN user_outlets uo ON uo.user_id=u.id AND uo.outlet_id=o.outlet_id
  JOIN user_roles ur ON ur.user_id=u.id
  JOIN roles r ON r.id=ur.role_id AND r.code='WAITER'
  WHERE u.id=o.assigned_waiter_id AND u.status='ACTIVE'
  AND NOT EXISTS(
    SELECT 1 FROM user_roles x JOIN roles xr ON xr.id=x.role_id
    WHERE x.user_id=u.id AND xr.code='OWNER'
  )
);

UPDATE waiter_assignments wa
SET status='CANCELLED',released_at=now(),version=wa.version+1
FROM orders o
WHERE wa.order_id=o.id AND wa.waiter_id=o.assigned_waiter_id
AND wa.status IN ('PENDING_ACCEPTANCE','ASSIGNED','TRANSFER_REQUESTED')
AND NOT EXISTS(
  SELECT 1 FROM users u
  JOIN user_outlets uo ON uo.user_id=u.id AND uo.outlet_id=o.outlet_id
  JOIN user_roles ur ON ur.user_id=u.id
  JOIN roles r ON r.id=ur.role_id AND r.code='WAITER'
  WHERE u.id=o.assigned_waiter_id AND u.status='ACTIVE'
  AND NOT EXISTS(
    SELECT 1 FROM user_roles x JOIN roles xr ON xr.id=x.role_id
    WHERE x.user_id=u.id AND xr.code='OWNER'
  )
);

UPDATE orders o SET assigned_waiter_id=NULL
WHERE assigned_waiter_id IS NOT NULL
AND NOT EXISTS(
  SELECT 1 FROM users u
  JOIN user_outlets uo ON uo.user_id=u.id AND uo.outlet_id=o.outlet_id
  JOIN user_roles ur ON ur.user_id=u.id
  JOIN roles r ON r.id=ur.role_id AND r.code='WAITER'
  WHERE u.id=o.assigned_waiter_id AND u.status='ACTIVE'
  AND NOT EXISTS(
    SELECT 1 FROM user_roles x JOIN roles xr ON xr.id=x.role_id
    WHERE x.user_id=u.id AND xr.code='OWNER'
  )
);
