-- Migration: 0004_create_content_table
-- Description: Creates the `content` table — the generic entity representing
--              anything that can be booked (a movie, sports match, concert,
--              theatre production, or other event). A single Content item
--              can have many Shows (see 0007_create_shows_table).
-- Depends on: 0001_create_plans_table (set_updated_at()).

CREATE TABLE content (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type                  VARCHAR(20)  NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    language              VARCHAR(50),
    duration              INTEGER,
    genre                 VARCHAR(100),
    release_or_event_date DATE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_content_type CHECK (
        type IN ('MOVIE', 'SPORTS_MATCH', 'CONCERT', 'THEATRE', 'EVENT')
    ),
    CONSTRAINT chk_content_duration_positive CHECK (duration IS NULL OR duration > 0)
);

COMMENT ON TABLE content IS
    'Generic bookable entity (movie, sports match, concert, theatre, or '
    'other event). `type` is restricted with a CHECK constraint rather '
    'than a DB enum, so the supported content types can be extended with '
    'a plain migration instead of an ALTER TYPE.';
COMMENT ON COLUMN content.type IS 'One of: MOVIE, SPORTS_MATCH, CONCERT, THEATRE, EVENT.';
COMMENT ON COLUMN content.duration IS
    'Runtime in minutes, where applicable (e.g. a movie). Nullable: not every content type has a fixed duration.';
COMMENT ON COLUMN content.release_or_event_date IS
    'Coarse date associated with the content (e.g. a movie''s release date). '
    'Nullable: not every content type has one at the Content level — precise '
    'per-occurrence scheduling lives on shows.start_time / shows.end_time.';

CREATE TRIGGER trg_content_set_updated_at
    BEFORE UPDATE ON content
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
