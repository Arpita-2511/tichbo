-- Rollback: 0005_create_venues_table
-- Apply this AFTER 0006's and 0007's down (seats.venue_id and
-- shows.venue_id both reference venues).

DROP TRIGGER IF EXISTS trg_venues_set_updated_at ON venues;
DROP TABLE IF EXISTS venues;
