-- Rollback: 0012_add_booking_sync_status_to_payments

DROP INDEX IF EXISTS idx_payments_booking_sync_status_pending;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_booking_sync_status;
ALTER TABLE payments DROP COLUMN IF EXISTS booking_sync_status;
