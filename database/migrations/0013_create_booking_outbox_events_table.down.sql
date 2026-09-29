-- Rollback: 0013_create_booking_outbox_events_table
-- No other migration depends on booking_outbox_events; safe to roll back
-- on its own.

DROP TRIGGER IF EXISTS trg_booking_outbox_events_set_updated_at ON booking_outbox_events;
DROP INDEX IF EXISTS idx_booking_outbox_events_pending_created_at;
DROP TABLE IF EXISTS booking_outbox_events;
