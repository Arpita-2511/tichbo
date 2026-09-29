package com.eventtick.audit.consumer;

import com.eventtick.audit.service.BookingEventAuditService;
import com.eventtick.audit.service.PersistOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test for {@link BookingCreatedConsumer}'s own
 * classification/acknowledgment logic — {@link BookingEventAuditService}
 * is mocked, so this proves the three-way branching (skip-and-ack /
 * duplicate-and-ack / fail-and-never-ack) directly, including the one
 * branch ({@link #onMessage_serviceThrowsAGenuineFailure_neverAcknowledges})
 * a real Kafka broker can't easily be made to exercise on demand.
 */
class BookingCreatedConsumerTest {

    private final BookingEventAuditService auditService = mock(BookingEventAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final BookingCreatedConsumer consumer = new BookingCreatedConsumer(auditService, objectMapper);
    private final Acknowledgment ack = mock(Acknowledgment.class);

    private static String envelope(String eventType, UUID eventId, UUID bookingId, UUID userId, UUID showId,
                                    String correlationId) {
        return """
                {"eventId":"%s","eventType":"%s","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"booking-service","aggregateType":"Booking","aggregateId":"%s",
                 "correlationId":"%s","causationId":null,
                 "payload":{"bookingId":"%s","userId":"%s","showId":"%s","seatIds":["%s"],"totalAmount":500.00}}
                """.formatted(eventId, eventType, bookingId, correlationId, bookingId, userId, showId, UUID.randomUUID());
    }

    @Test
    void onMessage_validBookingCreated_persistsAndAcknowledges() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        when(auditService.persist(eq(eventId), eq("BookingCreated"), eq(bookingId), eq(userId), eq(showId),
                any(Instant.class), eq("corr-1"))).thenReturn(PersistOutcome.PERSISTED);

        consumer.onMessage(envelope("BookingCreated", eventId, bookingId, userId, showId, "corr-1"), ack);

        verify(auditService).persist(eq(eventId), eq("BookingCreated"), eq(bookingId), eq(userId), eq(showId),
                any(Instant.class), eq("corr-1"));
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_duplicateEvent_stillAcknowledges() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        when(auditService.persist(any(), any(), any(), any(), any(), any(), any())).thenReturn(PersistOutcome.DUPLICATE);

        consumer.onMessage(envelope("BookingCreated", eventId, bookingId, userId, showId, "corr-1"), ack);

        verify(ack).acknowledge();
    }

    @Test
    void onMessage_malformedJson_doesNotCallTheService_stillAcknowledges() {
        consumer.onMessage("{not valid json", ack);

        verify(auditService, never()).persist(any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_unknownEventType_doesNotCallTheService_stillAcknowledges() {
        consumer.onMessage(envelope("SomethingElse", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "corr-1"), ack);

        verify(auditService, never()).persist(any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_missingBookingIdInPayload_doesNotCallTheService_stillAcknowledges() {
        UUID eventId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"BookingCreated","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"booking-service","aggregateType":"Booking","aggregateId":"%s",
                 "correlationId":"corr-1","causationId":null,
                 "payload":{"userId":"%s","showId":"%s","seatIds":[],"totalAmount":500.00}}
                """.formatted(eventId, eventId, UUID.randomUUID(), UUID.randomUUID());

        consumer.onMessage(json, ack);

        verify(auditService, never()).persist(any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_missingPayloadEntirely_doesNotCallTheService_stillAcknowledges() {
        UUID eventId = UUID.randomUUID();
        String json = """
                {"eventId":"%s","eventType":"BookingCreated","eventVersion":1,"occurredAt":"2026-09-28T10:00:00Z",
                 "producer":"booking-service","aggregateType":"Booking","aggregateId":"%s",
                 "correlationId":"corr-1","causationId":null}
                """.formatted(eventId, eventId);

        consumer.onMessage(json, ack);

        verify(auditService, never()).persist(any(), any(), any(), any(), any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onMessage_serviceThrowsAGenuineFailure_neverAcknowledges() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        when(auditService.persist(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("unexpected DB failure"));

        assertThatThrownBy(() -> consumer.onMessage(
                envelope("BookingCreated", eventId, bookingId, userId, showId, "corr-1"), ack))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(ack, never()).acknowledge();
    }

    @Test
    void onMessage_multipleValidEvents_eachPersistedAndAcknowledgedOnce() {
        for (int i = 0; i < 3; i++) {
            UUID eventId = UUID.randomUUID();
            UUID bookingId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID showId = UUID.randomUUID();
            when(auditService.persist(eq(eventId), any(), eq(bookingId), eq(userId), eq(showId), any(), any()))
                    .thenReturn(PersistOutcome.PERSISTED);

            consumer.onMessage(envelope("BookingCreated", eventId, bookingId, userId, showId, "corr-" + i), ack);
        }

        verify(auditService, times(3)).persist(any(), any(), any(), any(), any(), any(), any());
        verify(ack, times(3)).acknowledge();
    }
}
