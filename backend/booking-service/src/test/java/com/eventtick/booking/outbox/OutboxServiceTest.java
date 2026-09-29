package com.eventtick.booking.outbox;

import com.eventtick.booking.event.BookingCreatedPayload;
import com.eventtick.booking.event.EventTopics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test for {@link OutboxService} — the repository is
 * mocked, so this proves what {@link #record} builds and hands to it, not
 * real persistence (see {@code OutboxEventRepositoryTest} and
 * {@code BookingCreationOutboxIntegrationTest} for that).
 */
class OutboxServiceTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OutboxService outboxService = new OutboxService(outboxEventRepository, objectMapper, "booking-service");

    @Test
    void record_savesExactlyOneRow_withAFreshEventId() {
        UUID bookingId = UUID.randomUUID();
        BookingCreatedPayload payload = new BookingCreatedPayload(
                bookingId, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), new BigDecimal("100.00"));

        outboxService.record("BookingCreated", 1, "Booking", bookingId, EventTopics.BOOKING, payload, "corr-1", null);

        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }

    @Test
    void record_setsAggregateTypeAndIdAndTopic_onTheSavedRow() {
        UUID bookingId = UUID.randomUUID();
        BookingCreatedPayload payload = new BookingCreatedPayload(
                bookingId, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), new BigDecimal("100.00"));
        OutboxEvent[] captured = new OutboxEvent[1];
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return captured[0];
        });

        outboxService.record("BookingCreated", 1, "Booking", bookingId, EventTopics.BOOKING, payload, "corr-1", null);

        assertThat(captured[0].getEventType()).isEqualTo("BookingCreated");
        assertThat(captured[0].getAggregateType()).isEqualTo("Booking");
        assertThat(captured[0].getAggregateId()).isEqualTo(bookingId);
        assertThat(captured[0].getTopic()).isEqualTo(EventTopics.BOOKING);
        assertThat(captured[0].getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(captured[0].getAttempts()).isZero();
    }

    @Test
    void record_carriesTheGivenCorrelationId_intoTheSerializedPayload() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingCreatedPayload payload = new BookingCreatedPayload(
                bookingId, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), new BigDecimal("100.00"));
        OutboxEvent[] captured = new OutboxEvent[1];
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> captured[0] = inv.getArgument(0));

        outboxService.record("BookingCreated", 1, "Booking", bookingId, EventTopics.BOOKING, payload, "gw-request-id-42", null);

        JsonNode json = objectMapper.readTree(captured[0].getPayload());
        assertThat(json.get("correlationId").asText()).isEqualTo("gw-request-id-42");
    }

    @Test
    void record_withNoCorrelationId_generatesOne_ratherThanLeavingItBlank() throws Exception {
        // The background-event case (docs/architecture.md §47.5/§27):
        // no originating HTTP request, so no X-Request-ID to reuse.
        UUID bookingId = UUID.randomUUID();
        BookingCreatedPayload payload = new BookingCreatedPayload(
                bookingId, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), new BigDecimal("100.00"));
        OutboxEvent[] captured = new OutboxEvent[1];
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> captured[0] = inv.getArgument(0));

        outboxService.record("BookingCreated", 1, "Booking", bookingId, EventTopics.BOOKING, payload, null, null);

        JsonNode json = objectMapper.readTree(captured[0].getPayload());
        String correlationId = json.get("correlationId").asText();
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull(); // does not throw
    }

    @Test
    void record_eventIdOnTheRow_matchesTheEventIdInsideItsOwnSerializedPayload() throws Exception {
        UUID bookingId = UUID.randomUUID();
        BookingCreatedPayload payload = new BookingCreatedPayload(
                bookingId, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), new BigDecimal("100.00"));
        OutboxEvent[] captured = new OutboxEvent[1];
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> captured[0] = inv.getArgument(0));

        outboxService.record("BookingCreated", 1, "Booking", bookingId, EventTopics.BOOKING, payload, "corr-1", null);

        JsonNode json = objectMapper.readTree(captured[0].getPayload());
        assertThat(json.get("eventId").asText()).isEqualTo(captured[0].getEventId().toString());
    }

    @Test
    void twoCallsToRecord_produceTwoDifferentEventIds() {
        UUID bookingId = UUID.randomUUID();
        BookingCreatedPayload payload = new BookingCreatedPayload(
                bookingId, UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), new BigDecimal("100.00"));
        OutboxEvent[] captured = new OutboxEvent[2];
        int[] i = {0};
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> captured[i[0]++] = inv.getArgument(0));

        outboxService.record("BookingCreated", 1, "Booking", bookingId, EventTopics.BOOKING, payload, "corr-1", null);
        outboxService.record("BookingCreated", 1, "Booking", bookingId, EventTopics.BOOKING, payload, "corr-1", null);

        assertThat(captured[0].getEventId()).isNotEqualTo(captured[1].getEventId());
    }
}
