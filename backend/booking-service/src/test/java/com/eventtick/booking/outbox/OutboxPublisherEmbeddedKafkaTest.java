package com.eventtick.booking.outbox;

import com.eventtick.booking.event.EventTopics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OutboxPublisher} against a real, in-process Kafka broker
 * ({@code @EmbeddedKafka}) — the same "real infrastructure over a mock"
 * choice already established for Redis (embedded-redis) and applied to
 * Kafka in this module's own pom.xml comment. Proves what actually reaches
 * the broker (topic, key, value), not merely that {@code send()} was
 * called.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {EventTopics.BOOKING})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class OutboxPublisherEmbeddedKafkaTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    /**
     * Both tests in this class share one embedded broker/topic for the
     * whole Spring context (one {@code @EmbeddedKafka} instance), so a
     * fresh, never-before-used consumer group still sees every record ever
     * published to the topic during this class's run, not just "since I
     * started listening" — {@code auto.offset.reset=earliest} with nothing
     * ever committed means every poll starts from the beginning. Each test
     * below polls everything currently on the topic and finds its own
     * record by key, rather than assuming it is the only one there.
     */
    private ConsumerRecords<String, String> pollAllCurrentRecords() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "outbox-publisher-test-" + UUID.randomUUID(), "false", embeddedKafkaBroker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props)) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, EventTopics.BOOKING);
            return KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        }
    }

    @Test
    void publishPending_realAck_marksTheRowPublished_andActuallyReachesTheBroker() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"aggregateId\":\"" + aggregateId + "\"}";
        outboxEventRepository.saveAndFlush(new OutboxEvent(eventId, "BookingCreated", "Booking", aggregateId,
                EventTopics.BOOKING, payload, Instant.now()));

        int published = outboxPublisher.publishPending();

        assertThat(published).isEqualTo(1);
        OutboxEvent reloaded = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(reloaded.getPublishedAt()).isNotNull();

        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        pollAllCurrentRecords().forEach(r -> {
            if (aggregateId.toString().equals(r.key())) {
                matching.add(r);
            }
        });
        // aggregateId as the Kafka message key (docs/architecture.md §47.7).
        assertThat(matching).hasSize(1);
        assertThat(matching.get(0).value()).isEqualTo(payload);
    }

    @Test
    void publishPending_onlyPublishesPendingRows_notAlreadyPublishedOnes() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        outboxEventRepository.saveAndFlush(new OutboxEvent(eventId, "BookingCreated", "Booking", aggregateId,
                EventTopics.BOOKING, "{}", Instant.now()));

        int firstSweep = outboxPublisher.publishPending();
        int secondSweep = outboxPublisher.publishPending();

        assertThat(firstSweep).isEqualTo(1);
        assertThat(secondSweep).isZero();
        long timesThisAggregateWasPublished = 0;
        for (ConsumerRecord<String, String> r : pollAllCurrentRecords()) {
            if (aggregateId.toString().equals(r.key())) {
                timesThisAggregateWasPublished++;
            }
        }
        assertThat(timesThisAggregateWasPublished).isEqualTo(1);
    }
}
