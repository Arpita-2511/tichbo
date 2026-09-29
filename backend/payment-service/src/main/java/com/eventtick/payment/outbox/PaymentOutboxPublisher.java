package com.eventtick.payment.outbox;

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
 * Reads {@code PENDING} rows from {@code payment_outbox_events} and
 * publishes each to Kafka — mirrors booking-service's own {@code
 * OutboxPublisher} exactly, including every reasoning point in its class
 * Javadoc ({@code fixedDelay} not {@code fixedRate}; at-least-once by
 * design; concurrent publishers race harmlessly). Completely independent
 * of, and running well after, whatever business transaction wrote the row
 * — a publish failure here can never roll back or otherwise affect a
 * payment that has already reached {@code SUCCESS} (docs/architecture.md
 * §50's central architectural requirement: Kafka availability is never a
 * precondition for payment success).
 */
@Component
public class PaymentOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxPublisher.class);
    private static final long ACK_TIMEOUT_SECONDS = 5;

    private final PaymentOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;

    public PaymentOutboxPublisher(PaymentOutboxEventRepository outboxEventRepository,
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
            log.info("payment outbox sweep published {} event(s)", published);
        }
    }

    /**
     * Publishes up to {@code eventtick.events.publish-batch-size} {@code
     * PENDING} rows, oldest first. A plain public method, separate from
     * the {@code @Scheduled} wrapper, so tests can call it directly rather
     * than waiting on a timer.
     *
     * @return how many rows this call actually published
     */
    public int publishPending() {
        List<PaymentOutboxEvent> batch = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, PageRequest.of(0, batchSize));
        int publishedCount = 0;
        for (PaymentOutboxEvent row : batch) {
            if (publishOne(row)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    private boolean publishOne(PaymentOutboxEvent row) {
        try {
            // aggregateId (paymentId) as the Kafka message key — guarantees
            // every event for one payment lands in the same partition.
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
            // rest of this batch, and must never affect the payment this
            // event describes, which is already durably committed as
            // SUCCESS regardless of what happens here.
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
