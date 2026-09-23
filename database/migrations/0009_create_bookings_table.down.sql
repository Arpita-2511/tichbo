-- Rollback: 0009_create_bookings_table
-- Apply this AFTER 0010's down (booking_seats.booking_id references bookings).

DROP TRIGGER IF EXISTS trg_bookings_set_updated_at ON bookings;
DROP INDEX IF EXISTS idx_bookings_status;
DROP INDEX IF EXISTS idx_bookings_show_id;
DROP INDEX IF EXISTS idx_bookings_user_id;
DROP TABLE IF EXISTS bookings;
