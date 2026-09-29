package com.eventtick.audit.repository;

import com.eventtick.audit.entity.BookingEventAudit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real H2 persistence for {@link BookingEventAudit} — a genuine database
 * round trip, not a mock. {@code create-drop} scoped to this test class
 * only via {@link TestPropertySource} (audit-service's shared test config
 * keeps {@code ddl-auto: none}, matching booking-service's own precedent
 * for the identical situation — see its {@code OutboxEventRepositoryTest}).
 * Full {@code @SpringBootTest}, not {@code @DataJpaTest} — the same
 * precedent, for the same reason ({@code @DataJpaTest}'s own slice
 * auto-configuration produced surprising read-your-own-write failures
 * there).
 */
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class BookingEventAuditRepositoryTest {

    @Autowired
    private BookingEventAuditRepository auditRepository;

    private static BookingEventAudit row(UUID eventId) {
        return new BookingEventAudit(eventId, "BookingCreated", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Instant.now(), "corr-1");
    }

    @Test
    void savedRow_readsBackWithEveryField() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        BookingEventAudit saved = new BookingEventAudit(eventId, "BookingCreated", bookingId, userId, showId,
                occurredAt, "corr-abc");

        auditRepository.saveAndFlush(saved);
        BookingEventAudit reloaded = auditRepository.findById(eventId).orElseThrow();

        assertThat(reloaded.getEventType()).isEqualTo("BookingCreated");
        assertThat(reloaded.getBookingId()).isEqualTo(bookingId);
        assertThat(reloaded.getUserId()).isEqualTo(userId);
        assertThat(reloaded.getShowId()).isEqualTo(showId);
        assertThat(reloaded.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(reloaded.getCorrelationId()).isEqualTo("corr-abc");
        assertThat(reloaded.getProcessedAt()).isNotNull();
    }

    @Test
    void eventId_uniqueConstraint_isEnforced() {
        UUID eventId = UUID.randomUUID();
        auditRepository.saveAndFlush(row(eventId));

        assertThatThrownBy(() -> auditRepository.saveAndFlush(row(eventId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsById_findsAnAlreadyPersistedEvent() {
        UUID eventId = UUID.randomUUID();
        assertThat(auditRepository.existsById(eventId)).isFalse();

        auditRepository.saveAndFlush(row(eventId));

        assertThat(auditRepository.existsById(eventId)).isTrue();
    }
}
