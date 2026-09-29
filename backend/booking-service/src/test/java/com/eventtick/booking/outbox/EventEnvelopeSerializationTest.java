package com.eventtick.booking.outbox;

import com.eventtick.booking.event.BookingCreatedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EventEnvelope} serializes to plain, inspectable JSON (a human or
 * {@code kafka-console-consumer} can read it with no Java classpath
 * knowledge — docs/architecture.md §47.5) and every field survives a
 * round trip. A plain unit test, no Spring context: {@code
 * findAndRegisterModules()} picks up {@code jackson-datatype-jsr310} (for
 * {@code Instant}) from the classpath the same way Spring Boot's own
 * auto-configured {@code ObjectMapper} bean already does — not reinventing
 * Jackson configuration, just matching it standalone.
 */
class EventEnvelopeSerializationTest {

    // findAndRegisterModules() alone isn't quite enough: Jackson's own
    // default for Instant is WRITE_DATES_AS_TIMESTAMPS=true (an epoch
    // number, not ISO-8601). Spring Boot's auto-configured ObjectMapper
    // bean disables this by default; matching that here so this standalone
    // mapper serializes occurredAt the same way the real, Spring-managed
    // one (injected into OutboxService in production) actually does.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static EventEnvelope sampleEnvelope() {
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        BookingCreatedPayload payload = new BookingCreatedPayload(
                bookingId, userId, showId, List.of(seatId), new BigDecimal("500.00"));
        return new EventEnvelope(UUID.randomUUID(), "BookingCreated", 1, Instant.parse("2026-09-28T10:00:00Z"),
                "booking-service", "Booking", bookingId, "corr-1", null, payload);
    }

    @Test
    void serializesEveryEnvelopeField_asPlainInspectableJson() throws Exception {
        EventEnvelope envelope = sampleEnvelope();

        String json = objectMapper.writeValueAsString(envelope);
        ObjectNode node = (ObjectNode) objectMapper.readTree(json);

        assertThat(node.get("eventId").asText()).isEqualTo(envelope.eventId().toString());
        assertThat(node.get("eventType").asText()).isEqualTo("BookingCreated");
        assertThat(node.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(node.get("producer").asText()).isEqualTo("booking-service");
        assertThat(node.get("aggregateType").asText()).isEqualTo("Booking");
        assertThat(node.get("aggregateId").asText()).isEqualTo(envelope.aggregateId().toString());
        assertThat(node.get("correlationId").asText()).isEqualTo("corr-1");
        assertThat(node.get("causationId").isNull()).isTrue();
        assertThat(node.has("occurredAt")).isTrue();
        assertThat(node.has("payload")).isTrue();
    }

    @Test
    void payloadFields_areNestedInThePayloadObject_notFlattened() throws Exception {
        EventEnvelope envelope = sampleEnvelope();
        BookingCreatedPayload payload = (BookingCreatedPayload) envelope.payload();

        String json = objectMapper.writeValueAsString(envelope);
        ObjectNode payloadNode = (ObjectNode) objectMapper.readTree(json).get("payload");

        assertThat(payloadNode.get("bookingId").asText()).isEqualTo(payload.bookingId().toString());
        assertThat(payloadNode.get("userId").asText()).isEqualTo(payload.userId().toString());
        assertThat(payloadNode.get("showId").asText()).isEqualTo(payload.showId().toString());
        assertThat(payloadNode.get("seatIds").get(0).asText()).isEqualTo(payload.seatIds().get(0).toString());
        assertThat(payloadNode.get("totalAmount").decimalValue()).isEqualByComparingTo("500.00");
    }

    @Test
    void deserializesBackToAnEquivalentEnvelope() throws Exception {
        EventEnvelope envelope = sampleEnvelope();
        String json = objectMapper.writeValueAsString(envelope);

        // Deserialized generically (Object payload), matching how a
        // consumer with no producer-side class on its classpath would
        // read this — proves the JSON itself carries every field, not
        // that Java-to-Java round-tripping happens to work.
        var tree = objectMapper.readTree(json);
        assertThat(UUID.fromString(tree.get("eventId").asText())).isEqualTo(envelope.eventId());
        assertThat(tree.get("eventType").asText()).isEqualTo(envelope.eventType());
        assertThat(Instant.parse(tree.get("occurredAt").asText())).isEqualTo(envelope.occurredAt());
    }

    @Test
    void twoEnvelopesForTheSameLogicalEvent_getDifferentEventIds() {
        // eventId is generated fresh each time (by OutboxService, in
        // production) — this documents that EventEnvelope itself takes
        // whatever id it's given rather than deriving one, so uniqueness
        // is the caller's responsibility (see OutboxServiceTest).
        EventEnvelope first = sampleEnvelope();
        EventEnvelope second = sampleEnvelope();

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }
}
