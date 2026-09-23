-- Rollback: 0007_create_shows_table
-- No other migration in 0004-0007 depends on `shows`; safe to roll back
-- first among them.

DROP TRIGGER IF EXISTS trg_shows_set_updated_at ON shows;
DROP INDEX IF EXISTS idx_shows_start_time;
DROP INDEX IF EXISTS idx_shows_venue_id;
DROP INDEX IF EXISTS idx_shows_content_id;
DROP TABLE IF EXISTS shows;
