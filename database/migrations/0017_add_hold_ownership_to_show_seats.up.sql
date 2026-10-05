-- Phase 22: Temporary Seat Hold Ownership + Expiration
--
-- Adds hold-ownership columns to show_seats so the system can track which
-- user holds a seat and when that hold expires. All three columns are
-- nullable: they are populated only while status = 'HELD' and cleared
-- when the seat returns to AVAILABLE or moves to BOOKED.

ALTER TABLE show_seats
    ADD COLUMN holder_user_id  UUID,
    ADD COLUMN held_at         TIMESTAMPTZ,
    ADD COLUMN hold_expires_at TIMESTAMPTZ;

-- FK to users.id — SET NULL on delete so a deleted user doesn't orphan
-- the row; the scheduled cleanup will release the now-ownerless hold.
ALTER TABLE show_seats
    ADD CONSTRAINT fk_show_seats_holder_user
        FOREIGN KEY (holder_user_id) REFERENCES users (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE;

-- Partial index for the scheduled expiration sweep: only HELD rows with
-- an expiry in the past need scanning, ordered by expiry so the oldest
-- are released first.
CREATE INDEX idx_show_seats_hold_expires_at
    ON show_seats (hold_expires_at)
    WHERE status = 'HELD';
