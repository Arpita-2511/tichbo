package com.eventtick.audit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry policy for {@code BookingCreatedConsumer} (docs/architecture.md
 * §49.7/§49.9). Spring Boot's own Kafka autoconfiguration applies any
 * single {@link DefaultErrorHandler} bean found in the context to the
 * auto-configured listener container factory — no factory needs to be
 * hand-built just to install this.
 *
 * <p>Retries every second, forever ({@link FixedBackOff#UNLIMITED_ATTEMPTS}
 * — not a small fixed count) for whatever exception reaches the container
 * (a genuine, not-a-duplicate persistence failure — see {@code
 * BookingEventAuditService#persist}'s Javadoc). A bounded retry count
 * would eventually give up and silently skip the record, which is exactly
 * what "the message must remain retryable" (this step's own requirement)
 * rules out. This is deliberately not a complex backoff/DLQ framework —
 * just "keep trying" — see {@code BookingCreatedConsumer}'s own Javadoc
 * for where a future DLQ would actually fit (permanently unprocessable
 * messages, which this handler never sees: those are classified and
 * acknowledged — skipped, not retried — inside the listener itself,
 * before any exception would reach here).
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
