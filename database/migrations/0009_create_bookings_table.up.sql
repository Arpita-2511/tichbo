-- Migration: 0009_create_bookings_table
-- Description: Creates `bookings` — a user's booking for exactly one show.
--              The individual seats within a booking are recorded in
--              `booking_seats` (see 0010_create_booking_seats_table).
-- Depends on: 0002_create_users_table (users.id),
--             0007_create_shows_table (shows.id).

CREATE TABLE bookings (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL,
    show_id       UUID          NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    total_amount  NUMERIC(10,2) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'FAILED')),
    CONSTRAINT chk_bookings_total_amount_nonneg CHECK (total_amount >= 0),
    CONSTRAINT fk_bookings_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_bookings_show
        FOREIGN KEY (show_id) REFERENCES shows (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE bookings IS
    'A user''s booking for exactly one show. Line items (which seats) live '
    'in booking_seats; a booking''s seats must all belong to shows.id = '
    'bookings.show_id, but that cross-table invariant is not enforced by a '
    'DB constraint here — see booking_seats for details.';
COMMENT ON COLUMN bookings.status IS
    'One of: PENDING, CONFIRMED, CANCELLED, FAILED. Defaults to PENDING '
    'for a newly created booking, before seat holds/payment resolve it.';
COMMENT ON COLUMN bookings.total_amount IS
    'Total charged for this booking. Expected to equal SUM(booking_seats.price_at_booking) '
    'for this booking at creation time; stored on the header (rather than '
    'always recomputed) so the charged total is preserved even if seat '
    'prices are edited later. No default: the Booking Service must compute '
    'and set it explicitly.';

-- Postgres does not auto-index FK columns. status is indexed for admin
-- monitoring/filtering (e.g. "all PENDING bookings").
CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_show_id ON bookings (show_id);
CREATE INDEX idx_bookings_status  ON bookings (status);

CREATE TRIGGER trg_bookings_set_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
