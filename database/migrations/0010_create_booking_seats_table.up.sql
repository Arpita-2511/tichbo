-- Migration: 0010_create_booking_seats_table
-- Description: Creates `booking_seats` — the seats included in a booking.
--              Links to show_seats (not seats directly), because what's
--              actually being reserved is "this physical seat, for this
--              show" — the same physical seat must remain bookable for a
--              different show, so seat_id is deliberately not
--              globally-unique here; the constraint that matters
--              (uq_show_seats_show_seat) lives on show_seats.
-- Depends on: 0008_create_show_seats_table (show_seats.id),
--             0009_create_bookings_table (bookings.id).

CREATE TABLE booking_seats (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID          NOT NULL,
    show_seat_id      UUID          NOT NULL,
    price_at_booking  NUMERIC(10,2) NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_booking_seats_price_nonneg CHECK (price_at_booking >= 0),
    -- A given seat can only appear once within the same booking (doesn't
    -- restrict it from appearing in other bookings over time).
    CONSTRAINT uq_booking_seats_booking_show_seat UNIQUE (booking_id, show_seat_id),
    CONSTRAINT fk_booking_seats_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_booking_seats_show_seat
        FOREIGN KEY (show_seat_id) REFERENCES show_seats (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE booking_seats IS
    'Line items of a booking: which show_seats (physical seat + show '
    'pairing) it includes. References show_seats rather than seats '
    'directly, so the same physical seat can appear in booking_seats rows '
    'for many different shows/bookings over time — only one *active* '
    'booking should ever hold a given show_seats row at once, and that '
    'exclusivity is enforced via show_seats.status transitions guarded by '
    'row-level locking (see 0008_create_show_seats_table), not by a '
    'uniqueness constraint on this table. No created_at/updated_at trigger '
    'here: a line item is written once and not mutated in place — if a '
    'seat is removed from a booking, the row is deleted, not updated.';
COMMENT ON COLUMN booking_seats.price_at_booking IS
    'Snapshot of show_seats.price at the moment this seat was added to the '
    'booking. Stored separately from show_seats.price (which may change '
    'later) so a completed booking''s charged amount never silently '
    'changes if an admin edits show_seats pricing afterward. No default: '
    'the Booking Service must copy it from show_seats.price explicitly.';

-- Postgres does not auto-index FK columns. booking_id lookups ("seats in
-- this booking") are already served by uq_booking_seats_booking_show_seat's
-- leading column; show_seat_id alone is not, so it gets its own index
-- (e.g. to find which booking currently holds a given show_seat).
CREATE INDEX idx_booking_seats_show_seat_id ON booking_seats (show_seat_id);
