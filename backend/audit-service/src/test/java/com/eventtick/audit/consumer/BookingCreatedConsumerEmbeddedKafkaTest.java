package com.eventtick.audit.consumer;

import com.eventtick.audit.entity.BookingEventAudit;
import com.eventtick.audit.repository.BookingEventAuditRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BookingCreatedConsumer} against a real, in-process Kafka broker
 * ({@code @EmbeddedKafka}) — the same "real infrastructure over a mock"
 * choice already used throughout this project (Redis, booking-service's
 * own {@code OutboxPublisher} tests). Produces the exact JSON shape
 * booking-service's real producer emits and proves the consumer actually
 * persists it, not merely that a mocked service method was called.
 *
 * <p>All tests in this class share one embedded broker/topic and one H2
 * database for the whole Spring context — every assertion is therefore
 * scoped to a specific {@code eventId}/{@code bookingId} this test itself
 * produced, never a global row count (the same discipline
 * {@code OutboxPublisherEmbeddedKafkaTest} in booking-service already
 * established, for the identical reason).
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {EventTopics.BOOKING})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class BookingCreatedConsumerEmbeddedKafkaTest {

    @Autowired
    private BookingEventAuditRepository auditRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, String> producer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(pf);
    }

    /** The exact envelope shape booking-service's real OutboxService/EventEnvelope produce. */
    private static String realBookingCreatedEnvelope(UUID eventId, UUID bookingId, UUID userId, UUID showId,
                                                       String correlationId) {
        return """
                {"eventId":"%s","eventType":"BookingCreated","eventVersion":1,"occurredAt":"2026-09-28T10:15:00Z",
                 "producer":"booking-service","aggregateType":"Booking","aggregateId":"%s",
                 "correlationId":"%s","causationId":null,
                 "payload":{"bookingId":"%s","userId":"%s","showId":"%s","seatIds":["%s"],"totalAmount":500.00}}
                """.formatted(eventId, bookingId, correlationId, bookingId, userId, showId, UUID.randomUUID());
    }

    private BookingEventAudit awaitRow(UUID eventId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<BookingEventAudit> row = auditRepository.findById(eventId);
            if (row.isPresent()) {
                return row.get();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("audit row for eventId=" + eventId + " never appeared within the timeout");
    }

    @Test
    void aRealBookingCreatedEnvelope_isConsumedAndPersistedCorrectly() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        KafkaTemplate<String, String> kafka = producer();
            kafka.send(new ProducerRecord<>(EventTopics.BOOKING, bookingId.toString(),
                    realBookingCreatedEnvelope(eventId, bookingId, userId, showId, "corr-live-1"))).get();

        BookingEventAudit row = awaitRow(eventId);

        assertThat(row.getEventType()).isEqualTo("BookingCreated");
        assertThat(row.getBookingId()).isEqualTo(bookingId);
        assertThat(row.getUserId()).isEqualTo(userId);
        assertThat(row.getShowId()).isEqualTo(showId);
        assertThat(row.getCorrelationId()).isEqualTo("corr-live-1");
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-09-28T10:15:00Z"));
        assertThat(row.getProcessedAt()).isNotNull();
    }

    @Test
    void multipleEvents_acrossDifferentAggregateIds_areAllConsumed() throws Exception {
        UUID[] eventIds = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        UUID[] bookingIds = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        KafkaTemplate<String, String> kafka = producer();
            for (int i = 0; i < 3; i++) {
                kafka.send(new ProducerRecord<>(EventTopics.BOOKING, bookingIds[i].toString(),
                        realBookingCreatedEnvelope(eventIds[i], bookingIds[i], UUID.randomUUID(), UUID.randomUUID(),
                                "corr-multi-" + i))).get();
        }

        for (int i = 0; i < 3; i++) {
            BookingEventAudit row = awaitRow(eventIds[i]);
            assertThat(row.getBookingId()).isEqualTo(bookingIds[i]);
            assertThat(row.getCorrelationId()).isEqualTo("corr-multi-" + i);
        }
    }

    @Test
    void duplicateDelivery_ofTheSameEventId_producesExactlyOneRow() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        String envelope = realBookingCreatedEnvelope(eventId, bookingId, userId, showId, "corr-dup-1");

        KafkaTemplate<String, String> kafka = producer();
            kafka.send(new ProducerRecord<>(EventTopics.BOOKING, bookingId.toString(), envelope)).get();
            awaitRow(eventId); // wait for the first delivery to be fully processed

            // Same eventId, same everything — simulates Kafka's own
            // at-least-once redelivery (e.g. a rebalance before the first
            // delivery's offset commit reached the broker).
            kafka.send(new ProducerRecord<>(EventTopics.BOOKING, bookingId.toString(), envelope)).get();

        // Give the second delivery time to be (safely) processed too.
        Thread.sleep(3000);

        long matchingRows = auditRepository.findAll().stream()
                .filter(r -> r.getEventId().equals(eventId))
                .count();
        assertThat(matchingRows).isEqualTo(1);
    }

    @Test
    void malformedEvent_isNeverPersisted_andDoesNotBlockLaterValidEvents() throws Exception {
        UUID afterEventId = UUID.randomUUID();
        UUID afterBookingId = UUID.randomUUID();
        KafkaTemplate<String, String> kafka = producer();
            kafka.send(new ProducerRecord<>(EventTopics.BOOKING, "poison-key", "{not valid json")).get();
            kafka.send(new ProducerRecord<>(EventTopics.BOOKING, afterBookingId.toString(),
                    realBookingCreatedEnvelope(afterEventId, afterBookingId, UUID.randomUUID(), UUID.randomUUID(),
                            "corr-after-poison"))).get();

        // The malformed message is acknowledged (skipped), so the valid
        // one right after it is still consumed normally.
        BookingEventAudit row = awaitRow(afterEventId);
        assertThat(row.getBookingId()).isEqualTo(afterBookingId);
    }

    @Test
    void unknownEventType_isNeverPersisted() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String envelope = """
                {"eventId":"%s","eventType":"SomethingElse","eventVersion":1,"occurredAt":"2026-09-28T10:15:00Z",
                 "producer":"booking-service","aggregateType":"Booking","aggregateId":"%s",
                 "correlationId":"corr-unknown","causationId":null,
                 "payload":{"bookingId":"%s","userId":"%s","showId":"%s"}}
                """.formatted(eventId, bookingId, bookingId, UUID.randomUUID(), UUID.randomUUID());

        KafkaTemplate<String, String> kafka = producer();
            kafka.send(new ProducerRecord<>(EventTopics.BOOKING, bookingId.toString(), envelope)).get();
        Thread.sleep(2000);

        assertThat(auditRepository.findById(eventId)).isEmpty();
    }
}
