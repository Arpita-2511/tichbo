package com.eventtick.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The one place business code creates a payment-service outbox row —
 * mirrors booking-service's own {@code OutboxService} exactly, including
 * why it needs no {@code @Transactional} of its own (the inherited
 * {@code save(...)} already carries {@code @Transactional(propagation =
 * REQUIRED)}, joining whatever transaction the caller is already inside).
 * Structurally cannot publish to Kafka — no {@code KafkaTemplate}
 * dependency at all — the same "impossible to accidentally publish inside
 * the business transaction" property booking-service's class already has.
 *
 * <p><b>Every call site must be inside the same {@code @Transactional}
 * method as the state change the event describes.</b> For {@code
 * PaymentSucceeded} specifically, that call site is {@link
 * com.eventtick.payment.service.PaymentSuccessRecorder#recordSuccess} —
 * not {@code PaymentService} directly, since {@code PaymentService}'s own
 * entry points are deliberately not {@code @Transactional} (they span
 * outbound HTTP calls to booking-service/the payment provider — see that
 * class's own Javadoc) and Spring's proxy-based {@code @Transactional}
 * has no effect on a method called via {@code this.foo()} from within the
 * same class.
 */
@Service
public class PaymentOutboxService {

    private final PaymentOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String producer;

    public PaymentOutboxService(PaymentOutboxEventRepository outboxEventRepository,
                                 ObjectMapper objectMapper,
                                 @Value("${eventtick.events.producer}") String producer) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.producer = producer;
    }

    /**
     * Builds the envelope, serializes it to JSON, and writes it as a
     * {@code PENDING} outbox row — nothing more. {@link
     * PaymentOutboxPublisher} does the actual Kafka publish, later,
     * independently.
     *
     * @param eventVersion  per-event-type schema version, starting at 1
     * @param topic         the target Kafka topic — see {@link
     *                      com.eventtick.payment.event.EventTopics}
     * @param correlationId ties this event to the business transaction
     *                      that caused it (the originating request's own
     *                      X-Request-ID); a fresh id is generated if
     *                      blank, matching booking-service's own
     *                      background-event fallback (there is no
     *                      background-generated PaymentSucceeded in this
     *                      step — SUCCESS only ever happens synchronously
     *                      inside a request — but the fallback is kept for
     *                      the same reason it exists there: symmetry with
     *                      any future caller that has no originating
     *                      request).
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

        PaymentOutboxEvent row = new PaymentOutboxEvent(eventId, eventType, aggregateType, aggregateId,
                topic, serialize(envelope), occurredAt);
        outboxEventRepository.save(row);
    }

    private String serialize(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            // Same reasoning as booking-service's OutboxService: every
            // field is already Jackson-serializable, so this is only a
            // programming-error path. Failing loudly here, inside the
            // business transaction (which then rolls back), is correct.
            throw new IllegalStateException("Failed to serialize event envelope for " + envelope.eventType(), ex);
        }
    }
}
