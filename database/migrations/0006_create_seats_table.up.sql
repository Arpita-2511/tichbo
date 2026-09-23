-- Migration: 0006_create_seats_table
-- Description: Creates the `seats` table — the permanent physical seating
--              layout of a venue (e.g. Section A, Row 3, Seat 12). This is
--              NOT show-specific availability: whether a given seat is
--              available/held/booked for a particular Show belongs to the
--              future Booking domain, not here.
-- Depends on: 0005_create_venues_table (venues.id).

CREATE TABLE seats (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    venue_id    UUID        NOT NULL,
    section     VARCHAR(50) NOT NULL,
    "row"       VARCHAR(10) NOT NULL,
    seat_number INTEGER     NOT NULL,
    seat_type   VARCHAR(20) NOT NULL,

    CONSTRAINT chk_seats_seat_number_positive CHECK (seat_number > 0),
    CONSTRAINT chk_seats_seat_type CHECK (seat_type IN ('STANDARD', 'PREMIUM', 'VIP')),
    CONSTRAINT uq_seats_physical_seat UNIQUE (venue_id, section, "row", seat_number),
    CONSTRAINT fk_seats_venue
        FOREIGN KEY (venue_id) REFERENCES venues (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE seats IS
    'Permanent physical seats belonging to a venue''s seating layout. '
    'Deliberately has no availability/booked/held state — that is '
    'per-Show data owned by the future Booking domain, not a property of '
    'the physical seat itself.';
COMMENT ON COLUMN seats."row" IS
    'Row label within the section (e.g. "A", "12"). Quoted in DDL/queries because ROW is a reserved SQL keyword.';
COMMENT ON COLUMN seats.seat_type IS
    'Physical seat category: STANDARD, PREMIUM, or VIP. This is unrelated '
    'to the Eventtick user''s subscription plan (plans.name) — do not '
    'conflate the two.';

-- Postgres does not auto-index FK columns.
CREATE INDEX idx_seats_venue_id ON seats (venue_id);
