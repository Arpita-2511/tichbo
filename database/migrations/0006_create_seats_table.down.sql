-- Rollback: 0006_create_seats_table
-- Apply this BEFORE 0005's down (seats.venue_id references venues).

DROP INDEX IF EXISTS idx_seats_venue_id;
DROP TABLE IF EXISTS seats;
