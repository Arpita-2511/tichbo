package com.eventtick.audit.consumer;

import com.eventtick.audit.service.BookingEventAuditService;
import com.eventtick.audit.service.PersistOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Consumes {@code eventtick.booking}, processing {@code BookingCreated}
 * and nothing else (docs/architecture.md §49). Reads the raw JSON as a
 * plain {@code String} and parses it with a flexible {@link JsonNode} tree
 * — not a shared, typed {@code EventEnvelope} class (this service has no
 * dependency on booking-service's own module; no shared Maven module was
 * created — see §49.1), and not Spring Kafka's class-based {@code
 * JsonDeserializer<T>} either, so a future producer-side schema change
 * ({@code eventVersion}) doesn't require this consumer's deserializer
 * configuration to change in lockstep.
 *
 * <h2>Three outcomes, one rule each</h2>
 * <ul>
 *   <li><b>Permanently unprocessable</b> (malformed JSON, a required field
 *   missing, or an event type other than {@code BookingCreated}): logged,
 *   <b>acknowledged</b> — skipped, never retried, since retrying can never
 *   fix any of these. This is exactly where a future DLQ belongs: instead
 *   of a log line, this branch would publish the raw envelope to
 *   {@code eventtick.booking.dlq} for manual inspection/replay. Not built
 *   in this step (no DLQ topic, no DLQ producer) — see §49.9.</li>
 *   <li><b>Duplicate</b> ({@code eventId} already recorded — a genuine
 *   at-least-once redelivery of an already-processed event): logged (by
 *   {@link BookingEventAuditService}), <b>acknowledged</b> — correctly
 *   processed, nothing left to do.</li>
 *   <li><b>Genuine persistence failure</b> (anything {@link
 *   BookingEventAuditService#persist} throws that isn't the expected
 *   duplicate case): propagates out of this method uncaught, <b>never
 *   acknowledged</b>. {@code KafkaConsumerConfig}'s error handler retries
 *   the same record indefinitely rather than skipping it — see that
 *   class's own Javadoc.</li>
 * </ul>
 *
 * <p>Acknowledgment happens only after {@code auditService.persist(...)}
 * returns normally (§49.6/§49.8) — {@code ack-mode: MANUAL_IMMEDIATE} means
 * nothing commits the Kafka offset until {@link Acknowledgment#acknowledge()}
 * is actually called here, and the database write (inside {@code persist})
 * has already committed by the time that call is reached. No distributed
 * transaction spans Postgres and Kafka; the ordering above is the entire
 * mechanism.
 */
@Component
public class BookingCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingCreatedConsumer.class);
    private static final String HANDLED_EVENT_TYPE = "BookingCreated";

    private final BookingEventAuditService auditService;
    private final ObjectMapper objectMapper;

    public BookingCreatedConsumer(BookingEventAuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code id} is fixed (not auto-generated) so a test can look this
     * specific container up by name in the {@code
     * KafkaListenerEndpointRegistry} to stop/restart it and prove offset
     * persistence — see {@code BookingCreatedConsumerRestartTest}.
     *
     * <p><b>{@code groupId} is set explicitly, not left to default from
     * {@code id}.</b> A real bug found by that same restart test: {@code
     * @KafkaListener}'s {@code id} attribute becomes the effective Kafka
     * consumer group id whenever one is set and {@code groupId} is not
     * given separately ({@code idIsGroup} defaults to {@code true}) — so
     * without this, the actual live group would have silently been
     * {@code "bookingCreatedListener"}, not the configured, meaningful,
     * restart-stable {@code eventtick-booking-audit} from {@code
     * application.yml}'s {@code spring.kafka.consumer.group-id} (docs/
     * architecture.md §49.3). Explicit {@code groupId} here restores that.
     */
    @KafkaListener(id = "bookingCreatedListener", groupId = "${spring.kafka.consumer.group-id}",
            topics = EventTopics.BOOKING)
    public void onMessage(String rawEnvelope, Acknowledgment ack) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(rawEnvelope);
        } catch (JsonProcessingException ex) {
            log.warn("malformed event on eventtick.booking — skipping, will not be retried "
                    + "(future DLQ territory, see class Javadoc): {}", ex.toString());
            ack.acknowledge();
            return;
        }

        String eventType = textOrNull(envelope, "eventType");
        if (!HANDLED_EVENT_TYPE.equals(eventType)) {
            log.info("ignoring event of type '{}' on eventtick.booking — this consumer only handles {}",
                    eventType, HANDLED_EVENT_TYPE);
            ack.acknowledge();
            return;
        }

        UUID eventId = uuidOrNull(envelope, "eventId");
        Instant occurredAt = instantOrNull(envelope, "occurredAt");
        String correlationId = textOrNull(envelope, "correlationId");
        JsonNode payload = envelope.get("payload");
        UUID bookingId = payload == null ? null : uuidOrNull(payload, "bookingId");
        UUID userId = payload == null ? null : uuidOrNull(payload, "userId");
        UUID showId = payload == null ? null : uuidOrNull(payload, "showId");

        if (eventId == null || occurredAt == null || correlationId == null
                || bookingId == null || userId == null || showId == null) {
            log.warn("BookingCreated event is missing a required field — skipping, will not be retried: {}",
                    rawEnvelope);
            ack.acknowledge();
            return;
        }

        PersistOutcome outcome = auditService.persist(eventId, eventType, bookingId, userId, showId,
                occurredAt, correlationId);
        if (outcome == PersistOutcome.PERSISTED) {
            log.info("recorded BookingCreated audit: eventId={} bookingId={} correlationId={}",
                    eventId, bookingId, correlationId);
        }
        ack.acknowledge();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Instant instantOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
