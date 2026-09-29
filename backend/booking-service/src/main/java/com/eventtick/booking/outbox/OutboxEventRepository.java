package com.eventtick.booking.outbox;

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
 * Spring Data JPA repository for {@link OutboxEvent} (the
 * {@code booking_outbox_events} table).
 *
 * <p>{@code @Transactional} on {@link #markPublished} and
 * {@link #recordFailure}: unlike inherited CRUD methods (save, delete,
 * ...), which {@code SimpleJpaRepository} already wraps transactionally, a
 * custom {@code @Modifying @Query} method is <b>not</b> auto-wrapped by
 * Spring Data JPA — calling one with no ambient transaction throws
 * {@code TransactionRequiredException} at the Hibernate level. This exact
 * gap was found and fixed for {@code PaymentRepository}'s equivalent
 * methods in Phase 15 Step 3 (see docs/architecture.md §25.3); applied
 * here proactively rather than rediscovered by a failing test.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** A bounded batch of PENDING rows, oldest first — {@link OutboxPublisher}'s own query shape. */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = com.eventtick.booking.outbox.OutboxEventStatus.PUBLISHED, "
            + "o.publishedAt = :publishedAt WHERE o.eventId = :eventId")
    void markPublished(@Param("eventId") UUID eventId, @Param("publishedAt") Instant publishedAt);

    /**
     * Never changes {@code status} — a failed attempt stays {@code PENDING}
     * (see {@link OutboxEventStatus}'s own Javadoc) so the next sweep
     * retries it; only the failure bookkeeping is updated.
     */
    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.attempts = o.attempts + 1, o.lastError = :error WHERE o.eventId = :eventId")
    void recordFailure(@Param("eventId") UUID eventId, @Param("error") String error);
}
