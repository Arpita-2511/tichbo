-- Migration: 0005_create_venues_table
-- Description: Creates the `venues` table — the physical location where a
--              Show takes place. A venue can have many Seats (its permanent
--              seating layout, see 0006_create_seats_table) and can host
--              many Shows (see 0007_create_shows_table).
-- Depends on: 0001_create_plans_table (set_updated_at()).

CREATE TABLE venues (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE venues IS
    'Physical location where a Show takes place (e.g. a cinema, stadium, or theatre).';

CREATE TRIGGER trg_venues_set_updated_at
    BEFORE UPDATE ON venues
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
