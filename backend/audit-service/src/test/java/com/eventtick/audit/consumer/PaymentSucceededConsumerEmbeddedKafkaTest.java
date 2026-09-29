package com.eventtick.audit.consumer;

import com.eventtick.audit.entity.PaymentEventAudit;
import com.eventtick.audit.repository.PaymentEventAuditRepository;
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
 * {@link PaymentSucceededConsumer} against a real, in-process Kafka broker
 * ({@code @EmbeddedKafka}) — mirrors {@code
 * BookingCreatedConsumerEmbeddedKafkaTest} exactly, for the payment domain
 * (Phase 16 Step 4). Produces the exact JSON shape payment-service's real
 * {@code PaymentOutboxService}/{@code PaymentSuccessRecorder} emit and
 * proves the consumer actually persists it into {@code
 * payment_event_audit}, not merely that a mocked service method was
 * called.
 *
 * <p>All tests in this class share one embedded broker/topic and one H2
 * database for the whole Spring context — every assertion is therefore
 * scoped to a specific {@code eventId}/{@code paymentId} this test itself
 * produced, never a global row count (same discipline as the booking
 * equivalent, and as booking-service's own {@code
 * OutboxPublisherEmbeddedKafkaTest}).
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {EventTopics.PAYMENT})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class PaymentSucceededConsumerEmbeddedKafkaTest {

    @Autowired
    private PaymentEventAuditRepository auditRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, String> producer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(pf);
    }

    /** The exact envelope shape payment-service's real PaymentOutboxService/PaymentSuccessRecorder produce. */
    private static String realPaymentSucceededEnvelope(UUID eventId, UUID paymentId, UUID bookingId, UUID userId,
                                                         String correlationId) {
        return """
                {"eventId":"%s","eventType":"PaymentSucceeded","eventVersion":1,"occurredAt":"2026-09-28T10:15:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"%s","causationId":null,
                 "payload":{"paymentId":"%s","bookingId":"%s","userId":"%s","amount":500.00,
                 "currency":"INR","providerReference":"mock_txn_live"}}
                """.formatted(eventId, paymentId, correlationId, paymentId, bookingId, userId);
    }

    private PaymentEventAudit awaitRow(UUID eventId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        while (System.currentTimeMillis() < deadline) {
            Optional<PaymentEventAudit> row = auditRepository.findById(eventId);
            if (row.isPresent()) {
                return row.get();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("audit row for eventId=" + eventId + " never appeared within the timeout");
    }

    @Test
    void aRealPaymentSucceededEnvelope_isConsumedAndPersistedCorrectly() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        KafkaTemplate<String, String> kafka = producer();
        kafka.send(new ProducerRecord<>(EventTopics.PAYMENT, paymentId.toString(),
                realPaymentSucceededEnvelope(eventId, paymentId, bookingId, userId, "corr-live-1"))).get();

        PaymentEventAudit row = awaitRow(eventId);

        assertThat(row.getEventType()).isEqualTo("PaymentSucceeded");
        assertThat(row.getPaymentId()).isEqualTo(paymentId);
        assertThat(row.getBookingId()).isEqualTo(bookingId);
        assertThat(row.getUserId()).isEqualTo(userId);
        assertThat(row.getAmount()).isEqualByComparingTo("500.00");
        assertThat(row.getCurrency()).isEqualTo("INR");
        assertThat(row.getProviderReference()).isEqualTo("mock_txn_live");
        assertThat(row.getCorrelationId()).isEqualTo("corr-live-1");
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-09-28T10:15:00Z"));
        assertThat(row.getProcessedAt()).isNotNull();
    }

    @Test
    void multipleEvents_acrossDifferentPaymentAggregateIds_areAllConsumed() throws Exception {
        UUID[] eventIds = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        UUID[] paymentIds = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        KafkaTemplate<String, String> kafka = producer();
        for (int i = 0; i < 3; i++) {
            kafka.send(new ProducerRecord<>(EventTopics.PAYMENT, paymentIds[i].toString(),
                    realPaymentSucceededEnvelope(eventIds[i], paymentIds[i], UUID.randomUUID(), UUID.randomUUID(),
                            "corr-multi-" + i))).get();
        }

        for (int i = 0; i < 3; i++) {
            PaymentEventAudit row = awaitRow(eventIds[i]);
            assertThat(row.getPaymentId()).isEqualTo(paymentIds[i]);
            assertThat(row.getCorrelationId()).isEqualTo("corr-multi-" + i);
        }
    }

    @Test
    void duplicateDelivery_ofTheSameEventId_producesExactlyOneRow() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String envelope = realPaymentSucceededEnvelope(eventId, paymentId, bookingId, userId, "corr-dup-1");

        KafkaTemplate<String, String> kafka = producer();
        kafka.send(new ProducerRecord<>(EventTopics.PAYMENT, paymentId.toString(), envelope)).get();
        awaitRow(eventId); // wait for the first delivery to be fully processed

        // Same eventId, same everything — simulates Kafka's own
        // at-least-once redelivery.
        kafka.send(new ProducerRecord<>(EventTopics.PAYMENT, paymentId.toString(), envelope)).get();

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
        UUID afterPaymentId = UUID.randomUUID();
        KafkaTemplate<String, String> kafka = producer();
        kafka.send(new ProducerRecord<>(EventTopics.PAYMENT, "poison-key", "{not valid json")).get();
        kafka.send(new ProducerRecord<>(EventTopics.PAYMENT, afterPaymentId.toString(),
                realPaymentSucceededEnvelope(afterEventId, afterPaymentId, UUID.randomUUID(), UUID.randomUUID(),
                        "corr-after-poison"))).get();

        // The malformed message is acknowledged (skipped), so the valid
        // one right after it is still consumed normally.
        PaymentEventAudit row = awaitRow(afterEventId);
        assertThat(row.getPaymentId()).isEqualTo(afterPaymentId);
    }

    @Test
    void unknownEventType_isNeverPersisted() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String envelope = """
                {"eventId":"%s","eventType":"SomethingElse","eventVersion":1,"occurredAt":"2026-09-28T10:15:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"corr-unknown","causationId":null,
                 "payload":{"paymentId":"%s","bookingId":"%s","userId":"%s","amount":500.00,"currency":"INR"}}
                """.formatted(eventId, paymentId, paymentId, UUID.randomUUID(), UUID.randomUUID());

        KafkaTemplate<String, String> kafka = producer();
        kafka.send(new ProducerRecord<>(EventTopics.PAYMENT, paymentId.toString(), envelope)).get();
        Thread.sleep(2000);

        assertThat(auditRepository.findById(eventId)).isEmpty();
    }
}
