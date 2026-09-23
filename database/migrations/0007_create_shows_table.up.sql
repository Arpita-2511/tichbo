-- Migration: 0007_create_shows_table
-- Description: Creates the `shows` table — a specific scheduled occurrence
--              of a Content item at a Venue (e.g. "Avengers, 25 Sept,
--              7:30 PM, PVR XYZ"). This is where Content and Venue meet;
--              there is intentionally no direct Content -> Venue foreign key.
-- Depends on: 0004_create_content_table (content.id),
--             0005_create_venues_table (venues.id).

CREATE TABLE shows (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id  UUID        NOT NULL,
    venue_id    UUID        NOT NULL,
    start_time  TIMESTAMPTZ NOT NULL,
    end_time    TIMESTAMPTZ NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_shows_status CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT chk_shows_end_after_start CHECK (end_time > start_time),
    CONSTRAINT fk_shows_content
        FOREIGN KEY (content_id) REFERENCES content (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_shows_venue
        FOREIGN KEY (venue_id) REFERENCES venues (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE shows IS
    'A specific scheduled occurrence of Content at a Venue. Booking and '
    'seat-availability logic is intentionally NOT modeled here — that '
    'belongs to the future Booking domain, which will reference shows.id.';
COMMENT ON COLUMN shows.status IS
    'One of: SCHEDULED, CANCELLED, COMPLETED. Defaults to SCHEDULED for a newly created show.';

-- Postgres does not auto-index FK columns; content_id and venue_id are
-- joined/filtered on often. start_time is indexed for "shows starting
-- soon" / date-range browsing queries.
CREATE INDEX idx_shows_content_id ON shows (content_id);
CREATE INDEX idx_shows_venue_id   ON shows (venue_id);
CREATE INDEX idx_shows_start_time ON shows (start_time);

CREATE TRIGGER trg_shows_set_updated_at
    BEFORE UPDATE ON shows
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
