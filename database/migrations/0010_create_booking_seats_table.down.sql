-- Rollback: 0010_create_booking_seats_table
-- No other migration depends on booking_seats; safe to roll back first
-- among 0008-0010.

DROP INDEX IF EXISTS idx_booking_seats_show_seat_id;
DROP TABLE IF EXISTS booking_seats;
