-- Rollback: 0014_create_booking_event_audit_table
-- No other migration depends on booking_event_audit; safe to roll back on
-- its own.

DROP TABLE IF EXISTS booking_event_audit;
