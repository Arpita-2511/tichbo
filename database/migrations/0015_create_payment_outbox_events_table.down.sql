-- Rollback: 0015_create_payment_outbox_events_table
-- No other migration depends on payment_outbox_events; safe to roll back
-- on its own.

DROP TRIGGER IF EXISTS trg_payment_outbox_events_set_updated_at ON payment_outbox_events;
DROP INDEX IF EXISTS idx_payment_outbox_events_pending_created_at;
DROP TABLE IF EXISTS payment_outbox_events;
