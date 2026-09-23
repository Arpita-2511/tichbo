-- Migration: 0008_create_show_seats_table
-- Description: Creates `show_seats` — the availability of one physical seat
--              for one specific show. This is the join point between the
--              permanent `seats` layout and a particular `shows` occurrence:
--              the same physical seat is AVAILABLE for one show and BOOKED
--              for another, and price can differ per show.
-- Depends on: 0006_create_seats_table (seats.id),
--             0007_create_shows_table (shows.id).

CREATE TABLE show_seats (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    show_id     UUID          NOT NULL,
    seat_id     UUID          NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    price       NUMERIC(10,2) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_show_seats_status CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    CONSTRAINT chk_show_seats_price_nonneg CHECK (price >= 0),
    -- One row per physical seat per show — this is the uniqueness the
    -- Booking Service will later lock/update transactionally to prevent
    -- double booking (see table comment).
    CONSTRAINT uq_show_seats_show_seat UNIQUE (show_id, seat_id),
    CONSTRAINT fk_show_seats_show
        FOREIGN KEY (show_id) REFERENCES shows (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_show_seats_seat
        FOREIGN KEY (seat_id) REFERENCES seats (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE show_seats IS
    'Availability of one physical seat for one specific show. A physical '
    'seat (seats table) is permanent venue data; a show_seats row is what '
    'makes that seat AVAILABLE/HELD/BOOKED *for a particular show*, so the '
    'same seat can be independently available for one show and booked for '
    'another. The Booking Service is expected to transition a row''s '
    'status (AVAILABLE -> HELD -> BOOKED) inside a transaction that locks '
    'the row (e.g. SELECT ... FOR UPDATE) before updating it, using '
    'uq_show_seats_show_seat as the point of mutual exclusion — this table '
    'does not implement that logic itself, only the structure for it.';
COMMENT ON COLUMN show_seats.status IS
    'One of: AVAILABLE, HELD, BOOKED (see requirements FR-11/FR-12). '
    'Defaults to AVAILABLE for a newly created show_seats row.';
COMMENT ON COLUMN show_seats.price IS
    'Ticket price for this seat at this show. Stored per show_seats (not '
    'on seats or content) because the same physical seat/content can be '
    'priced differently show to show. No default: the Catalog/Show '
    'Service must set it explicitly.';

-- Postgres does not auto-index FK columns. show_id lookups ("seat map for
-- this show") are already served by uq_show_seats_show_seat's leading
-- column; seat_id alone is not, so it gets its own index.
CREATE INDEX idx_show_seats_seat_id ON show_seats (seat_id);

CREATE TRIGGER trg_show_seats_set_updated_at
    BEFORE UPDATE ON show_seats
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
