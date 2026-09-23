-- Rollback: 0008_create_show_seats_table
-- Apply this AFTER 0010's down (booking_seats.show_seat_id references show_seats).

DROP TRIGGER IF EXISTS trg_show_seats_set_updated_at ON show_seats;
DROP INDEX IF EXISTS idx_show_seats_seat_id;
DROP TABLE IF EXISTS show_seats;
