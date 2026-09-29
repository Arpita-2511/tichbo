package com.eventtick.booking.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real H2 persistence for {@link OutboxEvent}/{@link OutboxEventRepository}
 * — a genuine database round trip, not a mock. booking-service's own
 * shared test config keeps {@code ddl-auto: none} (see its own comment:
 * "no schema-generation mechanism for its H2 test database"), so this
 * class overrides just its own Spring context to {@code create-drop} via
 * {@link TestPropertySource} — every other booking-service test is
 * completely unaffected. Full {@code @SpringBootTest}, not
 * {@code @DataJpaTest}: matches payment-service's own established
 * precedent for this exact situation
 * ({@code PaymentRepositoryConstraintTest}) — {@code @DataJpaTest}'s own
 * slice-specific auto-configuration (a replaced datasource bean, its own
 * transactional-rollback wiring) produced surprising read-your-own-write
 * failures here that the plain full-context pattern does not.
 *
 * <p>Known, documented gap (matches {@code idx_payments_...} in
 * payment-service): H2 cannot create the real migration's partial index
 * ({@code idx_booking_outbox_events_pending_created_at}), and Hibernate's
 * {@code create-drop} generation does not attempt to replicate the
 * migration's hand-written CHECK constraints — only the real PostgreSQL
 * migration provides those. What this class does prove for real: the
 * {@code event_id} primary key /uniqueness constraint, and that a row
 * actually persists and reads back correctly.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static OutboxEvent pendingEvent(UUID eventId) {
        return new OutboxEvent(eventId, "BookingCreated", "Booking", UUID.randomUUID(),
                "eventtick.booking", "{\"eventId\":\"" + eventId + "\"}", Instant.now());
    }

    @Test
    void savedRow_readsBackWithEveryField() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        OutboxEvent row = new OutboxEvent(eventId, "BookingCreated", "Booking", aggregateId,
                "eventtick.booking", "{\"a\":1}", occurredAt);

        outboxEventRepository.saveAndFlush(row);
        OutboxEvent reloaded = outboxEventRepository.findById(eventId).orElseThrow();

        assertThat(reloaded.getEventType()).isEqualTo("BookingCreated");
        assertThat(reloaded.getAggregateType()).isEqualTo("Booking");
        assertThat(reloaded.getAggregateId()).isEqualTo(aggregateId);
        assertThat(reloaded.getTopic()).isEqualTo("eventtick.booking");
        assertThat(reloaded.getPayload()).isEqualTo("{\"a\":1}");
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(reloaded.getAttempts()).isZero();
        assertThat(reloaded.getPublishedAt()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void eventId_uniqueConstraint_isEnforced() {
        UUID eventId = UUID.randomUUID();
        outboxEventRepository.saveAndFlush(pendingEvent(eventId));

        assertThatThrownBy(() -> outboxEventRepository.saveAndFlush(pendingEvent(eventId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByStatusOrderByCreatedAtAsc_onlyReturnsThatStatus_respectsThePageLimit() {
        for (int i = 0; i < 3; i++) {
            outboxEventRepository.saveAndFlush(pendingEvent(UUID.randomUUID()));
        }
        UUID publishedId = UUID.randomUUID();
        OutboxEvent published = pendingEvent(publishedId);
        outboxEventRepository.saveAndFlush(published);
        outboxEventRepository.markPublished(publishedId, Instant.now());

        List<OutboxEvent> page = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, PageRequest.of(0, 2));

        assertThat(page).hasSize(2);
        assertThat(page).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.PENDING));
    }

    @Test
    void markPublished_setsStatusAndPublishedAt() {
        UUID eventId = UUID.randomUUID();
        outboxEventRepository.saveAndFlush(pendingEvent(eventId));
        Instant publishedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        outboxEventRepository.markPublished(eventId, publishedAt);
        outboxEventRepository.flush();
        OutboxEvent reloaded = outboxEventRepository.findById(eventId).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(reloaded.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void recordFailure_incrementsAttempts_setsLastError_leavesStatusPending() {
        UUID eventId = UUID.randomUUID();
        outboxEventRepository.saveAndFlush(pendingEvent(eventId));

        outboxEventRepository.recordFailure(eventId, "boom-1");
        outboxEventRepository.recordFailure(eventId, "boom-2");
        outboxEventRepository.flush();
        OutboxEvent reloaded = outboxEventRepository.findById(eventId).orElseThrow();

        assertThat(reloaded.getAttempts()).isEqualTo(2);
        assertThat(reloaded.getLastError()).isEqualTo("boom-2");
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }
}
