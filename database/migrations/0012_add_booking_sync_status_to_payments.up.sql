-- Migration: 0012_add_booking_sync_status_to_payments
-- Description: Adds `payments.booking_sync_status` — Phase 15 Step 3's
--              scenario-G reconciliation mechanism needs a durable,
--              database-backed signal for "has booking-service actually
--              acknowledged this payment's terminal outcome (confirm for
--              SUCCESS, cancel for FAILED/EXPIRED)". Without it, the only
--              way to find payments needing reconciliation after a service
--              restart would be re-querying booking-service for every
--              historical terminal payment forever — this column makes the
--              reconciliation sweep a single indexed query instead.
--              See docs/architecture.md §25.3.
-- Depends on: 0011_create_payments_table (payments table itself).

ALTER TABLE payments
    ADD COLUMN booking_sync_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_booking_sync_status
        CHECK (booking_sync_status IN ('PENDING', 'DONE'));

COMMENT ON COLUMN payments.booking_sync_status IS
    'Whether booking-service has acknowledged this payment''s terminal '
    'outcome (confirm for SUCCESS, cancel for FAILED/EXPIRED). PENDING '
    'until acknowledged; the reconciliation sweep retries exactly the rows '
    'still PENDING here. Meaningless for CREATED/PENDING/CANCELLED '
    'payments (CANCELLED never has a booking-service side effect to '
    'reconcile in this phase) — left at the default for those.';

-- The reconciliation sweep's own query shape: terminal payments
-- (status IN (SUCCESS,FAILED,EXPIRED)) not yet synced. A partial index
-- (only PENDING rows, the ones actually scanned) keeps the sweep cheap as
-- the table grows, since DONE rows vastly outnumber PENDING ones in the
-- steady state — the same "index only what the sweep queries" reasoning
-- already used for uq_payments_one_active_per_booking in migration 0011.
CREATE INDEX idx_payments_booking_sync_status_pending
    ON payments (status)
    WHERE booking_sync_status = 'PENDING';
