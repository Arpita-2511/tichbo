-- Migration: 0011_create_payments_table
-- Description: Creates `payments` — one payment attempt for exactly one
--              booking (owned by payment-service, Phase 15 Step 2). See
--              docs/architecture.md §25.1 for the full design this
--              implements (state model, idempotency, "at most one live
--              payment per booking").
-- Depends on: 0002_create_users_table (users.id),
--             0009_create_bookings_table (bookings.id).

CREATE TABLE payments (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID          NOT NULL,
    user_id             UUID          NOT NULL,
    amount              NUMERIC(10,2) NOT NULL,
    currency            CHAR(3)       NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
    provider            VARCHAR(30)   NOT NULL,
    provider_reference  VARCHAR(100),
    idempotency_key     VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_payments_status CHECK (status IN
        ('CREATED', 'PENDING', 'SUCCESS', 'FAILED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_payments_amount_nonneg CHECK (amount >= 0),
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payments_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_payments_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE payments IS
    'One payment attempt for exactly one booking (Phase 15 Step 2). Owned '
    'by payment-service, not booking-service — bookings/booking_seats/'
    'show_seats remain booking-service''s. A booking may have several '
    'historical payments over time (e.g. a failed attempt followed by a '
    'successful retry), but uq_payments_one_active_per_booking below '
    'guarantees at most one is ever "live" at once.';
COMMENT ON COLUMN payments.amount IS
    'Snapshot of the booking''s total_amount at payment-creation time — '
    'never client-supplied (see docs/architecture.md §25.1). NUMERIC(10,2), '
    'matching bookings.total_amount exactly, not a floating-point type.';
COMMENT ON COLUMN payments.currency IS
    'ISO 4217 currency code (e.g. ''INR''). No existing table in this '
    'schema has a currency column — every prior monetary field assumes one '
    'implicit, unnamed currency. The actual target currency is configured '
    'application-side (payment.default-currency), not hardcoded here.';
COMMENT ON COLUMN payments.status IS
    'One of: CREATED, PENDING, SUCCESS, FAILED, EXPIRED, CANCELLED. '
    'Defaults to CREATED for a newly created payment, before the provider '
    'is ever called. See docs/architecture.md §25.1 for the full state '
    'diagram and allowed transitions.';
COMMENT ON COLUMN payments.provider IS
    'Which PaymentProvider implementation handled this payment, e.g. '
    '''MOCK''. Not a payment-provider credential of any kind.';
COMMENT ON COLUMN payments.provider_reference IS
    'The provider''s own transaction id. NULL until the provider call '
    'resolves (and may remain NULL for a failure that never reached the '
    'provider, e.g. a validation rejection).';
COMMENT ON COLUMN payments.idempotency_key IS
    'Client-supplied, unique across all payments (uq_payments_idempotency_key). '
    'A repeated request with the same key and the same booking replays this '
    'row''s result instead of creating a duplicate or re-charging; a '
    'repeated request with the same key but a different booking is a '
    'conflict. See docs/architecture.md §25.1.';

-- At most one "live" (CREATED/PENDING/SUCCESS — see PaymentStatus.isLive())
-- payment per booking. This is the database-level enforcement of
-- "duplicate payment prevention" — the real guarantee against a race
-- condition, not merely an application-layer pre-check. Deliberately does
-- NOT enforce "one payment ever per booking": a booking whose only
-- payment(s) are FAILED/EXPIRED/CANCELLED may still accept a new payment
-- attempt, per the approved design.
CREATE UNIQUE INDEX uq_payments_one_active_per_booking
    ON payments (booking_id)
    WHERE status IN ('CREATED', 'PENDING', 'SUCCESS');

-- Postgres does not auto-index FK columns. booking_id is already covered
-- by uq_payments_one_active_per_booking's leading column for the "live
-- payment" lookup, but that index doesn't cover a plain "all payments for
-- this booking" scan (e.g. the booking-scoped GET, which also needs
-- terminal-status rows), so a plain index is still useful; user_id and
-- status are indexed for the same admin-monitoring/filtering reasons
-- bookings.user_id/status already are.
CREATE INDEX idx_payments_booking_id ON payments (booking_id);
CREATE INDEX idx_payments_user_id    ON payments (user_id);
CREATE INDEX idx_payments_status     ON payments (status);

CREATE TRIGGER trg_payments_set_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
