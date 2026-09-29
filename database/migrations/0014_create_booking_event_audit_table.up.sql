-- Migration: 0014_create_booking_event_audit_table
-- Description: Creates `booking_event_audit` — Phase 16 Step 3's first
--              Kafka consumer projection. Owned by the new, dedicated
--              audit-service (not booking-service): one row per
--              successfully consumed `BookingCreated` event from
--              `eventtick.booking`, written once and never updated. This
--              is an asynchronous event-consumption record, not a second
--              source of truth for bookings — it does not replace, join
--              against, or get read back by anything in the synchronous
--              booking/payment flow. See docs/architecture.md §49.
-- Depends on: nothing (booking_id/user_id/show_id are plain UUID
--             references, not foreign keys — see the table comment below).

CREATE TABLE booking_event_audit (
    event_id        UUID          PRIMARY KEY,
    event_type      VARCHAR(100)  NOT NULL,
    booking_id      UUID          NOT NULL,
    user_id         UUID          NOT NULL,
    show_id         UUID          NOT NULL,
    occurred_at     TIMESTAMPTZ   NOT NULL,
    correlation_id  VARCHAR(255)  NOT NULL,
    processed_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE booking_event_audit IS
    'One row per BookingCreated event this service has ever successfully '
    'consumed from eventtick.booking (Phase 16 Step 3). Owned by '
    'audit-service, a dedicated consumer service with no booking/payment/'
    'catalog/user domain state of its own — booking_id/user_id/show_id '
    'here are plain references copied from the event payload, not foreign '
    'keys, since this table lives in a different logical ownership '
    'boundary than bookings/users/shows (currently the same physical '
    'database, per docs/architecture.md §17, but never JPA-joined across '
    'that boundary — the same reasoning already applied to show_seats''s '
    'own show_id/seat_id columns). event_id is this table''s own primary '
    'key, not a separate surrogate id: it is the exact eventId from the '
    'consumed envelope, and its uniqueness is the database-level '
    'authority that makes re-consuming the same event (Kafka''s '
    'at-least-once delivery) a safe no-op rather than a duplicate row.';
COMMENT ON COLUMN booking_event_audit.event_id IS
    'The event envelope''s own eventId — the idempotency key. A second '
    'delivery of the same event reuses this exact value, so the INSERT '
    'fails on this primary key and is recognized as an already-processed '
    'duplicate rather than creating a second row.';
COMMENT ON COLUMN booking_event_audit.event_type IS
    'Always ''BookingCreated'' today — stored anyway (not hardcoded '
    'application-side) so this table stays meaningful if audit-service '
    'ever consumes more than one event type from eventtick.booking.';
COMMENT ON COLUMN booking_event_audit.occurred_at IS
    'The envelope''s own occurredAt — when the booking was actually '
    'created upstream, not when this service happened to consume it.';
COMMENT ON COLUMN booking_event_audit.correlation_id IS
    'The envelope''s own correlationId (the originating request''s '
    'X-Request-ID, propagated end to end from the Gateway through '
    'booking-service''s outbox into this row) — lets one booking attempt '
    'be traced across the Gateway''s own logs, booking-service, and this '
    'audit projection.';
COMMENT ON COLUMN booking_event_audit.processed_at IS
    'When THIS service actually inserted this row — distinct from '
    'occurred_at, which is the upstream event''s own timestamp. The gap '
    'between the two is, among other things, an observable measure of '
    'end-to-end pipeline latency (produce -> outbox -> Kafka -> consume).';

-- No trigger, no updated_at: a row is written exactly once and never
-- mutated afterward, the same "audit/line-item, not a mutable entity"
-- precedent booking_seats.created_at already establishes.
