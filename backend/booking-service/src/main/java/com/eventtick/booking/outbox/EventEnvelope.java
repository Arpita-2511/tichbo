package com.eventtick.booking.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * The event envelope approved in docs/architecture.md §47.5 / §27
 * (requirements.md). Every field from that design is present; none were
 * dropped. Serialized to JSON by {@link OutboxService} and published
 * byte-for-byte unchanged by {@link OutboxPublisher}.
 *
 * <p>{@code correlationId}/{@code causationId} are plain {@code String}
 * (not {@code UUID}), matching this step's own concrete instructions: a
 * correlation id is usually the Gateway's own {@code X-Request-ID} —
 * already validated as "1-128 characters of letters, digits, {@code
 * . _ : -}" by {@code RequestIdWebFilter}, not guaranteed to parse as a
 * {@code UUID} — so forcing a {@code UUID} type here would risk rejecting
 * a value the rest of the system already accepts.
 *
 * <p>{@code causationId} is nullable: none of the events this step
 * publishes are caused by consuming another Kafka event (they are all
 * caused directly by a committed database write, which {@code
 * correlationId} and {@code occurredAt} already capture) — it exists for a
 * future case, not because anything sets it today.
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
