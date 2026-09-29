package com.eventtick.payment.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * The event envelope approved in docs/architecture.md §47.5 / §27
 * (requirements.md) — payment-service's own copy, field-for-field
 * identical to booking-service's own {@code EventEnvelope} (Phase 16 Step
 * 2), not shared via a common module (§49.1). Serialized to JSON by
 * {@link PaymentOutboxService} and published byte-for-byte unchanged by
 * {@link PaymentOutboxPublisher}.
 *
 * <p>{@code correlationId}/{@code causationId} are plain {@code String}
 * (not {@code UUID}) for exactly the same reason as booking-service's own:
 * a correlation id is usually the Gateway's own {@code X-Request-ID},
 * already validated as "1-128 characters of letters, digits,
 * {@code . _ : -}" by {@code RequestIdWebFilter}, not guaranteed to parse
 * as a {@code UUID}.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String aggregateType,
        UUID aggregateId,
        String correlationId,
        String causationId,
        Object payload
) {
}
