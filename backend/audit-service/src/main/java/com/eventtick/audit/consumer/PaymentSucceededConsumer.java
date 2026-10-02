package com.eventtick.audit.consumer;

import com.eventtick.audit.service.PaymentEventAuditService;
import com.eventtick.audit.service.PersistOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

/**
 * Consumes {@code eventtick.payment}, processing {@code PaymentSucceeded}
 * (Phase 16 Step 4) and, as of Step 5, {@code PaymentFailed}/
 * {@code PaymentExpired} — the second Kafka consumer in this service,
 * added alongside {@link BookingCreatedConsumer} rather than in a new
 * service (Step 4's own instruction). Same raw-{@link JsonNode}-tree
 * parsing choice and the same reasons: no dependency on payment-service's
 * own classes, no shared Maven module, resilient to a future {@code
 * eventVersion} change.
 *
 * <p><b>One listener for all three event types, not three listeners.</b>
 * All three are produced to this same topic (payment-service's own single
 * outbox/topic design, docs/architecture.md §53) under the same consumer
 * group — a second or third {@code @KafkaListener} on {@code
 * eventtick.payment} with the same {@code groupId} would compete for the
 * same partitions rather than genuinely add coverage (Kafka partition
 * assignment is scoped per group, not per listener method), which is
 * exactly the "duplicated consumer logic" Step 5's own instructions ruled
 * out. Dispatch between the three is therefore a plain type check inside
 * this one method, not three competing subscriptions. {@code
 * PaymentFailed}/{@code PaymentExpired} reuse the exact same required-
 * field set and the exact same {@link PaymentEventAuditService#persist}
 * call as {@code PaymentSucceeded} — {@code failureReason} ({@code
 * PaymentFailed}-only) and the absence of {@code providerReference}
 * ({@code PaymentExpired}) are payload details this consumer doesn't need
 * to read: {@code payment_event_audit} (§50's existing schema) already
 * represents all three correctly without a migration.
 *
 * <h2>Same three outcomes as {@link BookingCreatedConsumer}, one rule each</h2>
 * <ul>
 *   <li><b>Permanently unprocessable</b> (malformed JSON, a required field
 *   missing, or an event type other than {@code PaymentSucceeded}): logged,
 *   <b>acknowledged</b> — skipped, never retried. Exactly where a future
 *   DLQ would fit (not built — see {@link BookingCreatedConsumer}'s own
 *   Javadoc for why).</li>
 *   <li><b>Duplicate</b> ({@code eventId} already recorded): logged (by
 *   {@link PaymentEventAuditService}), <b>acknowledged</b>.</li>
 *   <li><b>Genuine persistence failure:</b> propagates uncaught, <b>never
 *   acknowledged</b> — retried indefinitely by the same {@code
 *   KafkaConsumerConfig} error handler {@link BookingCreatedConsumer}
 *   already uses (one bean, applied to every listener container in this
 *   service — no second retry framework was introduced).</li>
 * </ul>
 *
 * <p>Acknowledgment happens only after {@code auditService.persist(...)}
 * returns normally — same manual, commit-then-acknowledge mechanism as
 * {@link BookingCreatedConsumer} ({@code ack-mode: MANUAL_IMMEDIATE}).
 */
@Component
public class PaymentSucceededConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentSucceededConsumer.class);
    private static final Set<String> HANDLED_EVENT_TYPES = Set.of("PaymentSucceeded", "PaymentFailed", "PaymentExpired");

    private final PaymentEventAuditService auditService;
    private final ObjectMapper objectMapper;

    public PaymentSucceededConsumer(PaymentEventAuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code id} is fixed for the same restart-lookup reason as {@code
     * BookingCreatedConsumer}'s own listener id (see {@code
     * PaymentSucceededConsumerRestartTest}).
     *
     * <p><b>{@code groupId} is a distinct, stable value —
     * {@code eventtick-payment-audit}, not {@code eventtick-booking-audit}
     * — set explicitly for the same reason {@link BookingCreatedConsumer}
     * sets its own explicitly</b> ({@code @KafkaListener}'s {@code id}
     * would otherwise silently become the effective group id). A separate
     * group id per independent consumption purpose (this step's own
     * instruction, §50.8): {@code eventtick.booking} and {@code
     * eventtick.payment} are two different topics with two entirely
     * independent consumption progress/offsets, and this service
     * legitimately runs two {@code @KafkaListener} methods, each wanting
     * its own restart-stable identity — sharing one group id across them
     * would be a real, if subtle, conflation of two unrelated consumption
     * streams (Kafka partition assignment/rebalancing is scoped per group,
     * not per listener method).
     */
    @KafkaListener(id = "paymentSucceededListener", groupId = "${eventtick.consumers.payment-audit-group-id}",
            topics = EventTopics.PAYMENT)
    public void onMessage(String rawEnvelope, Acknowledgment ack) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(rawEnvelope);
        } catch (JsonProcessingException ex) {
            log.warn("malformed event on eventtick.payment — skipping, will not be retried "
                    + "(future DLQ territory, see class Javadoc): {}", ex.toString());
            ack.acknowledge();
            return;
        }

        String eventType = textOrNull(envelope, "eventType");
        if (!HANDLED_EVENT_TYPES.contains(eventType)) {
            log.info("ignoring event of type '{}' on eventtick.payment — this consumer only handles {}",
                    eventType, HANDLED_EVENT_TYPES);
            ack.acknowledge();
            return;
        }

        UUID eventId = uuidOrNull(envelope, "eventId");
        Instant occurredAt = instantOrNull(envelope, "occurredAt");
        String correlationId = textOrNull(envelope, "correlationId");
        JsonNode payload = envelope.get("payload");
        UUID paymentId = payload == null ? null : uuidOrNull(payload, "paymentId");
        UUID bookingId = payload == null ? null : uuidOrNull(payload, "bookingId");
        UUID userId = payload == null ? null : uuidOrNull(payload, "userId");
        BigDecimal amount = payload == null ? null : decimalOrNull(payload, "amount");
        String currency = payload == null ? null : textOrNull(payload, "currency");
        // providerReference is intentionally not required for any of the
        // three types — always absent on PaymentExpired (see that
        // payload's own Javadoc), nullable on the other two; nullable in
        // the projection too.
        String providerReference = payload == null ? null : textOrNull(payload, "providerReference");

        if (eventId == null || occurredAt == null || correlationId == null
                || paymentId == null || bookingId == null || userId == null
                || amount == null || currency == null) {
            log.warn("{} event is missing a required field — skipping, will not be retried: {}",
                    eventType, rawEnvelope);
            ack.acknowledge();
            return;
        }

        PersistOutcome outcome = auditService.persist(eventId, eventType, paymentId, bookingId, userId,
                amount, currency, providerReference, occurredAt, correlationId);
        if (outcome == PersistOutcome.PERSISTED) {
            log.info("recorded {} audit: eventId={} paymentId={} bookingId={} correlationId={}",
                    eventType, eventId, paymentId, bookingId, correlationId);
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

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.decimalValue();
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
