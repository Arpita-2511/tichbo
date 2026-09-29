package com.eventtick.booking.outbox;

import com.eventtick.booking.event.EventTopics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OutboxPublisher} when the broker is completely unreachable
 * (bootstrap-servers pointed at {@code localhost:1}, nothing listening) —
 * the complement of {@code OutboxPublisherEmbeddedKafkaTest}'s happy path.
 * Docs/architecture.md §48's own claims: a publish failure retains the row
 * ({@code PENDING}, never lost), increments {@code attempts}, and never
 * throws out of {@link OutboxPublisher#publishPending()} — one bad send
 * must not stop the rest of a batch or crash the scheduled sweep.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.bootstrap-servers=localhost:1",
        "spring.kafka.producer.properties.max.block.ms=1000"
})
class OutboxPublisherUnavailableKafkaTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    private UUID seedPendingRow() {
        UUID eventId = UUID.randomUUID();
        outboxEventRepository.saveAndFlush(new OutboxEvent(eventId, "BookingCreated", "Booking", UUID.randomUUID(),
                EventTopics.BOOKING, "{}", Instant.now()));
        return eventId;
    }

    @Test
    void publishPending_kafkaUnreachable_doesNotThrow_andReportsZeroPublished() {
        seedPendingRow();

        int published = outboxPublisher.publishPending();

        assertThat(published).isZero();
    }

    @Test
    void publishPending_kafkaUnreachable_rowStaysPending_isNeverLost() {
        UUID eventId = seedPendingRow();

        outboxPublisher.publishPending();

        OutboxEvent reloaded = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(reloaded.getPublishedAt()).isNull();
    }

    @Test
    void publishPending_kafkaUnreachable_recordsTheFailure_andIncrementsAttempts_onEachSweep() {
        UUID eventId = seedPendingRow();

        outboxPublisher.publishPending();
        outboxPublisher.publishPending();
        outboxPublisher.publishPending();

        OutboxEvent reloaded = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.getAttempts()).isEqualTo(3);
        assertThat(reloaded.getLastError()).isNotBlank();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void publishPending_oneUnpublishableRow_doesNotStopTheRestOfTheBatch() {
        UUID unreachableTopicRow = seedPendingRow();
        UUID anotherRow = seedPendingRow();

        int published = outboxPublisher.publishPending();

        assertThat(published).isZero(); // both fail, since Kafka itself is unreachable for the whole batch
        assertThat(outboxEventRepository.findById(unreachableTopicRow).orElseThrow().getAttempts()).isEqualTo(1);
        assertThat(outboxEventRepository.findById(anotherRow).orElseThrow().getAttempts()).isEqualTo(1);
    }
}
