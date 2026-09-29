package com.eventtick.audit.consumer;

import com.eventtick.audit.entity.BookingEventAudit;
import com.eventtick.audit.repository.BookingEventAuditRepository;
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
 * Step 14's restart requirement, automated: the consumer group id is fixed
 * ({@code eventtick-booking-audit}, not regenerated per run — see
 * {@code application.yml}), so stopping and restarting the listener
 * container must resume from the last committed offset, not replay
 * already-processed events. Stops/restarts the real container via {@link
 * KafkaListenerEndpointRegistry} rather than the whole Spring context, the
 * closest in-JVM equivalent to a real process restart available without
 * tearing down and re-creating the embedded broker.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {EventTopics.BOOKING})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class BookingCreatedConsumerRestartTest {

    @Autowired
    private BookingEventAuditRepository auditRepository;

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

    private static String envelope(UUID eventId, UUID bookingId, String correlationId) {
        return """
                {"eventId":"%s","eventType":"BookingCreated","eventVersion":1,"occurredAt":"2026-09-28T10:15:00Z",
                 "producer":"booking-service","aggregateType":"Booking","aggregateId":"%s",
                 "correlationId":"%s","causationId":null,
                 "payload":{"bookingId":"%s","userId":"%s","showId":"%s","seatIds":["%s"],"totalAmount":500.00}}
                """.formatted(eventId, bookingId, correlationId, bookingId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
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
    void consumerGroupId_isFixed_notRandomlyGenerated() {
        MessageListenerContainer container = registry.getListenerContainer("bookingCreatedListener");
        assertThat(container).isNotNull();
        assertThat(container.getGroupId()).isEqualTo("eventtick-booking-audit");
    }

    @Test
    void restart_resumesFromTheLastCommittedOffset_doesNotReprocessAlreadyCommittedEvents() throws Exception {
        MessageListenerContainer container = registry.getListenerContainer("bookingCreatedListener");
        assertThat(container).isNotNull();

        UUID beforeRestartEventId = UUID.randomUUID();
        UUID beforeRestartBookingId = UUID.randomUUID();
        KafkaTemplate<String, String> kafka = producer();
        kafka.send(new ProducerRecord<>(EventTopics.BOOKING, beforeRestartBookingId.toString(),
                envelope(beforeRestartEventId, beforeRestartBookingId, "corr-before-restart"))).get();
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
        UUID afterRestartBookingId = UUID.randomUUID();
        kafka.send(new ProducerRecord<>(EventTopics.BOOKING, afterRestartBookingId.toString(),
                envelope(afterRestartEventId, afterRestartBookingId, "corr-after-restart"))).get();
        BookingEventAudit afterRow = awaitRow(afterRestartEventId);
        assertThat(afterRow.getBookingId()).isEqualTo(afterRestartBookingId);
    }
}
