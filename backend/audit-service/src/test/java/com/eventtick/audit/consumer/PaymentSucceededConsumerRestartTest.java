package com.eventtick.audit.consumer;

import com.eventtick.audit.entity.PaymentEventAudit;
import com.eventtick.audit.repository.PaymentEventAuditRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 14's restart requirement, automated, for the payment domain —
 * mirrors {@code BookingCreatedConsumerRestartTest} exactly. The consumer
 * group id is fixed ({@code eventtick-payment-audit}, distinct from
 * {@code eventtick-booking-audit} — not regenerated per run, see {@code
 * application.yml}), so stopping and restarting the listener container
 * must resume from the last committed offset, not replay already-processed
 * events.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {EventTopics.PAYMENT})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class PaymentSucceededConsumerRestartTest {

    @Autowired
    private PaymentEventAuditRepository auditRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    private KafkaTemplate<String, String> producer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(pf);
    }

    private static String envelope(UUID eventId, UUID paymentId, String correlationId) {
        return """
                {"eventId":"%s","eventType":"PaymentSucceeded","eventVersion":1,"occurredAt":"2026-09-28T10:15:00Z",
                 "producer":"payment-service","aggregateType":"Payment","aggregateId":"%s",
                 "correlationId":"%s","causationId":null,
                 "payload":{"paymentId":"%s","bookingId":"%s","userId":"%s","amount":500.00,
                 "currency":"INR","providerReference":"ref-restart"}}
                """.formatted(eventId, paymentId, correlationId, paymentId, UUID.randomUUID(), UUID.randomUUID());
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
    void consumerGroupId_isFixed_notRandomlyGenerated_andDistinctFromTheBookingConsumerGroup() {
        MessageListenerContainer container = registry.getListenerContainer("paymentSucceededListener");
        assertThat(container).isNotNull();
        assertThat(container.getGroupId()).isEqualTo("eventtick-payment-audit");

        MessageListenerContainer bookingContainer = registry.getListenerContainer("bookingCreatedListener");
        assertThat(bookingContainer).isNotNull();
        assertThat(bookingContainer.getGroupId()).isEqualTo("eventtick-booking-audit");
        assertThat(container.getGroupId()).isNotEqualTo(bookingContainer.getGroupId());
    }

    @Test
    void restart_resumesFromTheLastCommittedOffset_doesNotReprocessAlreadyCommittedEvents() throws Exception {
        MessageListenerContainer container = registry.getListenerContainer("paymentSucceededListener");
        assertThat(container).isNotNull();

        UUID beforeRestartEventId = UUID.randomUUID();
        UUID beforeRestartPaymentId = UUID.randomUUID();
        KafkaTemplate<String, String> kafka = producer();
        kafka.send(new ProducerRecord<>(EventTopics.PAYMENT, beforeRestartPaymentId.toString(),
                envelope(beforeRestartEventId, beforeRestartPaymentId, "corr-before-restart"))).get();
        awaitRow(beforeRestartEventId); // committed (acknowledged) before we ever stop the container

        container.stop();
        ContainerTestUtils.waitForAssignment(container, 0); // returns immediately once stopped; documents intent
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());

        // The pre-restart event must not have been reprocessed — still
        // exactly the one row, same as right after it was first consumed.
        long countForBeforeRestartEvent = auditRepository.findAll().stream()
                .filter(r -> r.getEventId().equals(beforeRestartEventId)).count();
        assertThat(countForBeforeRestartEvent).isEqualTo(1);

        // A new event produced after the restart is still consumed
        // normally — the group didn't just stop working.
        UUID afterRestartEventId = UUID.randomUUID();
        UUID afterRestartPaymentId = UUID.randomUUID();
        kafka.send(new ProducerRecord<>(EventTopics.PAYMENT, afterRestartPaymentId.toString(),
                envelope(afterRestartEventId, afterRestartPaymentId, "corr-after-restart"))).get();
        PaymentEventAudit afterRow = awaitRow(afterRestartEventId);
        assertThat(afterRow.getPaymentId()).isEqualTo(afterRestartPaymentId);
    }
}
