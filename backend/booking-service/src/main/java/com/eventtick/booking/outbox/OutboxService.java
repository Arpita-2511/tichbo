package com.eventtick.booking.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The one place business code creates an outbox row (docs/architecture.md
 * §47.9/§48). Deliberately has no dependency on {@code KafkaTemplate} or
 * any Kafka client at all — it can only write a database row, never send
 * anything to a broker. This isn't a convention callers must remember to
 * follow; it is structurally impossible to publish to Kafka through this
 * class, which is exactly the "make it difficult to accidentally publish
 * directly to Kafka inside the business transaction" requirement this
 * class exists to satisfy.
 *
 * <p><b>Why this needs no {@code @Transactional} of its own:</b> {@link
 * #record} calls {@code outboxEventRepository.save(...)}, an inherited
 * {@code SimpleJpaRepository} method that already carries its own
 * {@code @Transactional(propagation = REQUIRED)} — {@code REQUIRED} joins
 * an already-open transaction rather than starting a new one. Calling
 * {@link #record} from inside {@code BookingService.createBooking}'s own
 * {@code @Transactional} method means this save participates in that exact
 * same transaction/connection/commit boundary as the booking write, with
 * no coordination code needed — this is the entire mechanism that makes
 * the outbox atomic with the business write it accompanies. Calling this
 * method outside of an ambient transaction would still "work" (it starts
 * its own), but would defeat the whole point — every call site must be
 * inside the same {@code @Transactional} method as the state change the
 * event describes.
 */
@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String producer;

    public OutboxService(OutboxEventRepository outboxEventRepository,
                          ObjectMapper objectMapper,
                          @Value("${eventtick.events.producer}") String producer) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.producer = producer;
    }

    /**
     * Builds the envelope (docs/architecture.md §47.5), serializes it to
     * JSON, and writes it as a {@code PENDING} outbox row — nothing more.
     * {@link OutboxPublisher} does the actual Kafka publish, later,
     * independently.
     *
     * @param eventVersion  per-event-type schema version, starting at 1
     * @param topic         the target Kafka topic — see {@link EventTopics}
     * @param correlationId ties this event to the business transaction that
     *                      caused it (e.g. the originating request's own
     *                      X-Request-ID); a fresh id is generated if blank,
     *                      for a future event with no originating request
     * @param causationId   nullable — the eventId of the specific upstream
     *                      event that directly caused this one, if any
     */
    public void record(String eventType, int eventVersion, String aggregateType, UUID aggregateId,
                        String topic, Object payload, String correlationId, String causationId) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        String resolvedCorrelationId = (correlationId == null || correlationId.isBlank())
                ? UUID.randomUUID().toString()
                : correlationId;

        EventEnvelope envelope = new EventEnvelope(eventId, eventType, eventVersion, occurredAt,
                producer, aggregateType, aggregateId, resolvedCorrelationId, causationId, payload);

        OutboxEvent row = new OutboxEvent(eventId, eventType, aggregateType, aggregateId,
                topic, serialize(envelope), occurredAt);
        outboxEventRepository.save(row);
    }

    private String serialize(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            // Every field on EventEnvelope is a plain UUID/String/int/Instant
            // or another already-Jackson-serializable record — there is no
            // realistic runtime path here, only a programming error (e.g. a
            // future payload type Jackson genuinely cannot serialize).
            // Failing loudly here, inside the business transaction (which
            // then rolls back, per the outbox's own atomicity guarantee), is
            // correct: an event that cannot be represented must not silently
            // vanish, and must not leave the business write committed with
            // no corresponding outbox row.
            throw new IllegalStateException("Failed to serialize event envelope for " + envelope.eventType(), ex);
        }
    }
}
