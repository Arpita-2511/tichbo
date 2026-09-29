package com.eventtick.booking.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reads {@code PENDING} outbox rows and publishes each to Kafka —
 * completely independent of, and running well after, whatever business
 * transaction wrote the row (docs/architecture.md §47.9/§48). A publish
 * failure here can never roll back or otherwise affect the business write
 * that already committed; it only leaves the row {@code PENDING} for the
 * next sweep.
 *
 * <p><b>{@code fixedDelay}, not {@code fixedRate}</b> (the same choice
 * {@code PaymentLifecycleScheduler} already makes): the next sweep starts
 * only after the previous one finishes, so a slow or unreachable broker
 * cannot cause two sweeps to overlap.
 *
 * <p><b>At-least-once, by design</b> (docs/architecture.md §47.6): if this
 * process crashes after a broker ack but before {@link
 * OutboxEventRepository#markPublished} commits, the row is still {@code
 * PENDING} and the next sweep — from this instance after a restart, or
 * another instance entirely — publishes it again, under the same {@code
 * eventId}. This is expected and acceptable, not a bug to fix: consumers
 * dedupe on {@code eventId}, and nothing here claims exactly-once.
 * Concurrent publishers (multiple instances) can race for the same batch
 * of PENDING rows for the same reason — a duplicate publish, never a lost
 * one.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long ACK_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                            KafkaTemplate<String, String> kafkaTemplate,
                            @Value("${eventtick.events.publish-batch-size}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${eventtick.events.publish-sweep-interval-ms}")
    public void sweep() {
        int published = publishPending();
        if (published > 0) {
            log.info("outbox sweep published {} event(s)", published);
        }
    }

    /**
     * Publishes up to {@code eventtick.events.publish-batch-size} {@code
     * PENDING} rows, oldest first. A plain public method, separate from the
     * {@code @Scheduled} wrapper, specifically so tests can call it
     * directly rather than waiting on a timer (the same reason {@code
     * PaymentService#expirePendingPayments} is a plain method too).
     *
     * @return how many rows this call actually published
     */
    public int publishPending() {
        List<OutboxEvent> batch = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, PageRequest.of(0, batchSize));
        int publishedCount = 0;
        for (OutboxEvent row : batch) {
            if (publishOne(row)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    /**
     * @return {@code true} if this row was actually published and marked so
     */
    private boolean publishOne(OutboxEvent row) {
        try {
            // aggregateId as the Kafka message key (docs/architecture.md
            // §47.7): guarantees every event for one aggregate lands in the
            // same partition, and so is delivered in order relative to each
            // other. Blocking on the returned future, bounded by
            // ACK_TIMEOUT_SECONDS, is what makes this "require a real
            // acknowledgement" rather than "mark PUBLISHED merely because a
            // send was initiated" — see the class Javadoc on at-least-once.
            SendResult<String, String> result = kafkaTemplate
                    .send(row.getTopic(), row.getAggregateId().toString(), row.getPayload())
                    .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outboxEventRepository.markPublished(row.getEventId(), Instant.now());
            log.debug("published event {} (type={}, topic={}, partition={}, offset={})",
                    row.getEventId(), row.getEventType(), row.getTopic(),
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            return true;
        } catch (Exception ex) {
            // Never propagates: one bad/unreachable send must not stop the
            // rest of this batch, and must never affect the booking (or
            // whatever aggregate) this event describes, which is already
            // durably committed regardless of what happens here.
            outboxEventRepository.recordFailure(row.getEventId(), describe(ex));
            log.warn("failed to publish event {} (type={}, topic={}) — will retry next sweep: {}",
                    row.getEventId(), row.getEventType(), row.getTopic(), ex.toString());
            return false;
        }
    }

    private String describe(Exception ex) {
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }
}
