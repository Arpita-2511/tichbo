DROP INDEX IF EXISTS idx_show_seats_hold_expires_at;

ALTER TABLE show_seats DROP CONSTRAINT IF EXISTS fk_show_seats_holder_user;

ALTER TABLE show_seats
    DROP COLUMN IF EXISTS hold_expires_at,
    DROP COLUMN IF EXISTS held_at,
    DROP COLUMN IF EXISTS holder_user_id;
