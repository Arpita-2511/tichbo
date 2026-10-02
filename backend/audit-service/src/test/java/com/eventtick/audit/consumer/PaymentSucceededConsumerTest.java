package com.eventtick.audit.consumer;

import com.eventtick.audit.service.PaymentEventAuditService;
import com.eventtick.audit.service.PersistOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test for {@link PaymentSucceededConsumer}'s own
 * classification/acknowledgment logic — mirrors {@code
 * BookingCreatedConsumerTest} exactly, including the one branch
 * ({@link #onMessage_serviceThrowsAGenuineFailure_neverAcknowledges}) a
 * real Kafka broker can't easily be made to exercise on demand.
 */
class PaymentSucceededConsumerTest {

    private final PaymentEventAuditService auditService = mock(PaymentEventAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PaymentSucceededConsumer consumer = new PaymentSucceededConsumer(auditService, objectMapper);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private static String envelope(String eventType, UUID eventId, UUID paymentId, UUID bookingId, UUID userId,
                                    String correlationId) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"%s","causationId":null,
                 "payload":{"paymentId":"%s","bookingId":"%s","userId":"%s","amount":500.00,
                 "currency":"INR","providerReference":"ref-1"}}
                """.formatted(eventId, eventType, paymentId, correlationId, paymentId, bookingId, userId);
    }

    @Test
    void onMessage_validPaymentSucceeded_persistsAndAcknowledges() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(auditService.persist(eq(eventId), eq("PaymentSucceeded"), eq(paymentId), eq(bookingId), eq(userId),
                any(BigDecimal.class), eq("INR"), eq("ref-1"), any(Instant.class), eq("corr-1")))
                .thenReturn(PersistOutcome.PERSISTED);

        consumer.onMessage(envelope("PaymentSucceeded", eventId, paymentId, bookingId, userId, "corr-1"), ack);

        verify(auditService).persist(eq(eventId), eq("PaymentSucceeded"), eq(paymentId), eq(bookingId), eq(userId),
                any(BigDecimal.class), eq("INR"), eq("ref-1"), any(Instant.class), eq("corr-1"));
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_duplicateEvent_stillAcknowledges() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(auditService.persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PersistOutcome.DUPLICATE);

        consumer.onMessage(envelope("PaymentSucceeded", eventId, paymentId, bookingId, userId, "corr-1"), ack);

        verify(ack).acknowledge();
    }

    @Test
    void onMessage_malformedJson_doesNotCallTheService_stillAcknowledges() {
        consumer.onMessage("{not valid json", ack);

        verify(auditService, never()).persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_unknownEventType_doesNotCallTheService_stillAcknowledges() {
        consumer.onMessage(envelope("SomethingElse", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "corr-1"), ack);

        verify(auditService, never()).persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_missingPaymentIdInPayload_doesNotCallTheService_stillAcknowledges() {
        UUID eventId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"PaymentSucceeded","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"corr-1","causationId":null,
                 "payload":{"bookingId":"%s","userId":"%s","amount":500.00,"currency":"INR"}}
                """.formatted(eventId, eventId, UUID.randomUUID(), UUID.randomUUID());

        consumer.onMessage(json, ack);

        verify(auditService, never()).persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_missingAmount_doesNotCallTheService_stillAcknowledges() {
        UUID eventId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"PaymentSucceeded","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"corr-1","causationId":null,
                 "payload":{"paymentId":"%s","bookingId":"%s","userId":"%s","currency":"INR"}}
                """.formatted(eventId, eventId, eventId, UUID.randomUUID(), UUID.randomUUID());

        consumer.onMessage(json, ack);

        verify(auditService, never()).persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_missingPayloadEntirely_doesNotCallTheService_stillAcknowledges() {
        UUID eventId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"PaymentSucceeded","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"corr-1","causationId":null}
                """.formatted(eventId, eventId);

        consumer.onMessage(json, ack);

        verify(auditService, never()).persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_missingProviderReference_isStillProcessed_notARequiredField() {
        // providerReference is deliberately nullable (see
        // PaymentSucceededPayload's own Javadoc) — its absence must NOT
        // be treated the same as a genuinely missing required field.
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"PaymentSucceeded","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"corr-1","causationId":null,
                 "payload":{"paymentId":"%s","bookingId":"%s","userId":"%s","amount":500.00,"currency":"INR"}}
                """.formatted(eventId, paymentId, paymentId, bookingId, userId);
        when(auditService.persist(eq(eventId), any(), any(), any(), any(), any(), any(), isNull(), any(), any()))
                .thenReturn(PersistOutcome.PERSISTED);

        consumer.onMessage(json, ack);

        verify(auditService).persist(eq(eventId), eq("PaymentSucceeded"), eq(paymentId), eq(bookingId), eq(userId),
                any(BigDecimal.class), eq("INR"), isNull(), any(Instant.class), eq("corr-1"));
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_serviceThrowsAGenuineFailure_neverAcknowledges() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(auditService.persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("unexpected DB failure"));

        assertThatThrownBy(() -> consumer.onMessage(
                envelope("PaymentSucceeded", eventId, paymentId, bookingId, userId, "corr-1"), ack))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(ack, never()).acknowledge();
    }

    // ---- Phase 16 Step 5: PaymentFailed and PaymentExpired ----

    @Test
    void onMessage_validPaymentFailed_persistsAndAcknowledges() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(auditService.persist(eq(eventId), eq("PaymentFailed"), eq(paymentId), eq(bookingId), eq(userId),
                any(BigDecimal.class), eq("INR"), eq("ref-1"), any(Instant.class), eq("corr-f")))
                .thenReturn(PersistOutcome.PERSISTED);

        consumer.onMessage(envelope("PaymentFailed", eventId, paymentId, bookingId, userId, "corr-f"), ack);

        verify(auditService).persist(eq(eventId), eq("PaymentFailed"), eq(paymentId), eq(bookingId), eq(userId),
                any(BigDecimal.class), eq("INR"), eq("ref-1"), any(Instant.class), eq("corr-f"));
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_validPaymentExpired_persistsAndAcknowledges_withNullProviderReference() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"PaymentExpired","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"corr-e","causationId":null,
                 "payload":{"paymentId":"%s","bookingId":"%s","userId":"%s","amount":500.00,"currency":"INR"}}
                """.formatted(eventId, paymentId, paymentId, bookingId, userId);
        when(auditService.persist(eq(eventId), eq("PaymentExpired"), eq(paymentId), eq(bookingId), eq(userId),
                any(BigDecimal.class), eq("INR"), isNull(), any(Instant.class), eq("corr-e")))
                .thenReturn(PersistOutcome.PERSISTED);

        consumer.onMessage(json, ack);

        verify(auditService).persist(eq(eventId), eq("PaymentExpired"), eq(paymentId), eq(bookingId), eq(userId),
                any(BigDecimal.class), eq("INR"), isNull(), any(Instant.class), eq("corr-e"));
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_duplicatePaymentFailed_stillAcknowledges() {
        when(auditService.persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PersistOutcome.DUPLICATE);

        consumer.onMessage(envelope("PaymentFailed", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "corr-dup-f"), ack);

        verify(ack).acknowledge();
    }

    @Test
    void onMessage_duplicatePaymentExpired_stillAcknowledges() {
        when(auditService.persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PersistOutcome.DUPLICATE);

        String json = """
                {"eventId":"%s","eventType":"PaymentExpired","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"corr-dup-e","causationId":null,
                 "payload":{"paymentId":"%s","bookingId":"%s","userId":"%s","amount":500.00,"currency":"INR"}}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        consumer.onMessage(json, ack);

        verify(ack).acknowledge();
    }

    @Test
    void onMessage_multipleValidEvents_eachPersistedAndAcknowledgedOnce() {
        for (int i = 0; i < 3; i++) {
            UUID eventId = UUID.randomUUID();
            UUID paymentId = UUID.randomUUID();
            UUID bookingId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            when(auditService.persist(eq(eventId), any(), eq(paymentId), eq(bookingId), eq(userId),
                    any(), any(), any(), any(), any())).thenReturn(PersistOutcome.PERSISTED);

            consumer.onMessage(envelope("PaymentSucceeded", eventId, paymentId, bookingId, userId, "corr-" + i), ack);
        }

        verify(auditService, times(3)).persist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(ack, times(3)).acknowledge();
    }
}
