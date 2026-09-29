package com.eventtick.payment.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PaymentOutboxEvent} (the
 * {@code payment_outbox_events} table) — mirrors booking-service's own
 * {@code OutboxEventRepository} exactly, including the {@code
 * @Transactional} on the two {@code @Modifying @Query} methods (not
 * auto-wrapped by Spring Data JPA the way inherited CRUD methods are).
 */
public interface PaymentOutboxEventRepository extends JpaRepository<PaymentOutboxEvent, UUID> {

    /** A bounded batch of PENDING rows, oldest first — {@link PaymentOutboxPublisher}'s own query shape. */
    List<PaymentOutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE PaymentOutboxEvent o SET o.status = com.eventtick.payment.outbox.OutboxEventStatus.PUBLISHED, "
            + "o.publishedAt = :publishedAt WHERE o.eventId = :eventId")
    void markPublished(@Param("eventId") UUID eventId, @Param("publishedAt") Instant publishedAt);

    /**
     * Never changes {@code status} — a failed attempt stays {@code PENDING}
     * so the next sweep retries it; only the failure bookkeeping is
     * updated.
     */
    @Transactional
    @Modifying
    @Query("UPDATE PaymentOutboxEvent o SET o.attempts = o.attempts + 1, o.lastError = :error WHERE o.eventId = :eventId")
    void recordFailure(@Param("eventId") UUID eventId, @Param("error") String error);
}
