-- Rollback: 0016_create_payment_event_audit_table
-- No other migration depends on payment_event_audit; safe to roll back
-- on its own.

DROP TABLE IF EXISTS payment_event_audit;
