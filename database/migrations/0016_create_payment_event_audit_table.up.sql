-- Migration: 0016_create_payment_event_audit_table
-- Description: Creates `payment_event_audit` — Phase 16 Step 4's second
--              Kafka consumer projection, owned by the existing
--              audit-service (not a new service — see docs/architecture.md
--              §50). One row per successfully consumed `PaymentSucceeded`
--              event from `eventtick.payment`, written once and never
--              updated. Same "audit projection, not a second source of
--              truth" reasoning as `booking_event_audit` (migration 0014):
--              does not replace, join against, or get read back by
--              anything in the synchronous payment/booking flow.
-- Depends on: nothing (payment_id/booking_id/user_id are plain UUID
--             references, not foreign keys — see the table comment below).

CREATE TABLE payment_event_audit (
    event_id            UUID           PRIMARY KEY,
    event_type          VARCHAR(100)   NOT NULL,
    payment_id          UUID           NOT NULL,
    booking_id          UUID           NOT NULL,
    user_id             UUID           NOT NULL,
    amount              NUMERIC(10,2)  NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    provider_reference  VARCHAR(100),
    occurred_at         TIMESTAMPTZ    NOT NULL,
    correlation_id      VARCHAR(255)   NOT NULL,
    processed_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

COMMENT ON TABLE payment_event_audit IS
    'One row per PaymentSucceeded event this service has ever successfully '
    'consumed from eventtick.payment (Phase 16 Step 4). Owned by '
    'audit-service, the same dedicated consumer service booking_event_audit '
    'already lives in — no new service, no new domain state, still no '
    'booking/payment/catalog/user domain state of its own. payment_id/'
    'booking_id/user_id here are plain references copied from the event '
    'payload, not foreign keys, same reasoning as booking_event_audit''s '
    'own columns. event_id is this table''s own primary key, not a '
    'separate surrogate id: it is the exact eventId from the consumed '
    'envelope, and its uniqueness is the database-level authority that '
    'makes re-consuming the same event (Kafka''s at-least-once delivery) '
    'a safe no-op rather than a duplicate row.';
COMMENT ON COLUMN payment_event_audit.event_id IS
    'The event envelope''s own eventId — the idempotency key. A second '
    'delivery of the same event reuses this exact value, so the INSERT '
    'fails on this primary key and is recognized as an already-processed '
    'duplicate rather than creating a second row.';
COMMENT ON COLUMN payment_event_audit.event_type IS
    'Always ''PaymentSucceeded'' today — stored anyway (not hardcoded '
    'application-side) so this table stays meaningful if audit-service '
    'ever consumes more than one event type from eventtick.payment (e.g. '
    'a future PaymentFailed/PaymentExpired — not implemented this step).';
COMMENT ON COLUMN payment_event_audit.amount IS
    'The envelope payload''s own amount — a copy of the authoritative '
    'payments.amount at the moment the payment succeeded, for this '
    'projection''s own read convenience; never read back into the '
    'payment/booking flow.';
COMMENT ON COLUMN payment_event_audit.provider_reference IS
    'The envelope payload''s own providerReference. Nullable only because '
    'the column shape mirrors the source payload field-for-field; in '
    'practice PaymentSucceeded is never published without one (see '
    'PaymentSuccessRecorder).';
COMMENT ON COLUMN payment_event_audit.occurred_at IS
    'The envelope''s own occurredAt — when the payment actually reached '
    'SUCCESS upstream, not when this service happened to consume it.';
COMMENT ON COLUMN payment_event_audit.correlation_id IS
    'The envelope''s own correlationId (the originating POST /api/payments '
    'request''s X-Request-ID, propagated end to end from the Gateway '
    'through payment-service''s outbox into this row) — lets one payment '
    'attempt be traced across the Gateway''s own logs, payment-service, '
    'and this audit projection, the same as booking_event_audit''s own '
    'correlation_id.';
COMMENT ON COLUMN payment_event_audit.processed_at IS
    'When THIS service actually inserted this row — distinct from '
    'occurred_at, which is the upstream event''s own timestamp.';

-- No trigger, no updated_at: a row is written exactly once and never
-- mutated afterward, the same precedent booking_event_audit already sets.
