-- Rollback: 0011_create_payments_table
-- No other migration depends on payments; safe to roll back on its own.

DROP TRIGGER IF EXISTS trg_payments_set_updated_at ON payments;
DROP INDEX IF EXISTS idx_payments_status;
DROP INDEX IF EXISTS idx_payments_user_id;
DROP INDEX IF EXISTS idx_payments_booking_id;
DROP INDEX IF EXISTS uq_payments_one_active_per_booking;
DROP TABLE IF EXISTS payments;
