package com.eventtick.audit.service;

import com.eventtick.audit.entity.BookingEventAudit;
import com.eventtick.audit.repository.BookingEventAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain Mockito unit test for {@link BookingEventAuditService} — the repository is mocked. */
class BookingEventAuditServiceTest {

    private final BookingEventAuditRepository auditRepository = mock(BookingEventAuditRepository.class);
    private final BookingEventAuditService service = new BookingEventAuditService(auditRepository);

    private static UUID uuid() {
        return UUID.randomUUID();
    }

    @Test
    void persist_newEvent_insertsAndReturnsPersisted() {
        UUID eventId = uuid();

        PersistOutcome outcome = service.persist(eventId, "BookingCreated", uuid(), uuid(), uuid(),
                Instant.now(), "corr-1");

        assertThat(outcome).isEqualTo(PersistOutcome.PERSISTED);
        verify(auditRepository).saveAndFlush(any(BookingEventAudit.class));
    }

    @Test
    void persist_duplicateEventId_recognizedViaTheDatabaseConstraint_returnsDuplicate() {
        UUID eventId = uuid();
        when(auditRepository.saveAndFlush(any(BookingEventAudit.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(auditRepository.existsById(eventId)).thenReturn(true);

        PersistOutcome outcome = service.persist(eventId, "BookingCreated", uuid(), uuid(), uuid(),
                Instant.now(), "corr-1");

        assertThat(outcome).isEqualTo(PersistOutcome.DUPLICATE);
    }

    @Test
    void persist_constraintViolationThatIsNotTheDuplicateEventId_isNotSwallowed_rethrown() {
        // "Do not silently treat unrelated database errors as duplicates"
        // — existsById(eventId) says false, so this is NOT the expected
        // duplicate case; the original exception must propagate.
        UUID eventId = uuid();
        DataIntegrityViolationException original = new DataIntegrityViolationException("some other constraint");
        when(auditRepository.saveAndFlush(any(BookingEventAudit.class))).thenThrow(original);
        when(auditRepository.existsById(eventId)).thenReturn(false);

        assertThatThrownBy(() -> service.persist(eventId, "BookingCreated", uuid(), uuid(), uuid(),
                Instant.now(), "corr-1"))
                .isSameAs(original);
    }

    @Test
    void persist_savesTheExactFieldsGiven() {
        UUID eventId = uuid();
        UUID bookingId = uuid();
        UUID userId = uuid();
        UUID showId = uuid();
        Instant occurredAt = Instant.parse("2026-09-28T10:00:00Z");
        BookingEventAudit[] captured = new BookingEventAudit[1];
        when(auditRepository.saveAndFlush(any(BookingEventAudit.class))).thenAnswer(inv -> captured[0] = inv.getArgument(0));

        service.persist(eventId, "BookingCreated", bookingId, userId, showId, occurredAt, "corr-xyz");

        assertThat(captured[0].getEventId()).isEqualTo(eventId);
        assertThat(captured[0].getEventType()).isEqualTo("BookingCreated");
        assertThat(captured[0].getBookingId()).isEqualTo(bookingId);
        assertThat(captured[0].getUserId()).isEqualTo(userId);
        assertThat(captured[0].getShowId()).isEqualTo(showId);
        assertThat(captured[0].getOccurredAt()).isEqualTo(occurredAt);
        assertThat(captured[0].getCorrelationId()).isEqualTo("corr-xyz");
    }

    @Test
    void persist_duplicate_doesNotAttemptASecondInsert() {
        UUID eventId = uuid();
        when(auditRepository.saveAndFlush(any(BookingEventAudit.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(auditRepository.existsById(eventId)).thenReturn(true);

        service.persist(eventId, "BookingCreated", uuid(), uuid(), uuid(), Instant.now(), "corr-1");

        verify(auditRepository, never()).save(any());
    }
}
