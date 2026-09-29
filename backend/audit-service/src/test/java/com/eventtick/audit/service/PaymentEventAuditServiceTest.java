package com.eventtick.audit.service;

import com.eventtick.audit.entity.PaymentEventAudit;
import com.eventtick.audit.repository.PaymentEventAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test for {@link PaymentEventAuditService} — mirrors
 * {@code BookingEventAuditServiceTest} exactly, including the "DB failure
 * remains retryable" case (Phase 16 Step 4 automated test requirement
 * #16): a constraint violation that is NOT the expected {@code event_id}
 * duplicate must propagate uncaught, so {@code PaymentSucceededConsumer}
 * never acknowledges it and the message stays retryable.
 */
class PaymentEventAuditServiceTest {

    private final PaymentEventAuditRepository auditRepository = mock(PaymentEventAuditRepository.class);
    private final PaymentEventAuditService service = new PaymentEventAuditService(auditRepository);

    private static UUID uuid() {
        return UUID.randomUUID();
    }

    @Test
    void persist_newEvent_insertsAndReturnsPersisted() {
        UUID eventId = uuid();

        PersistOutcome outcome = service.persist(eventId, "PaymentSucceeded", uuid(), uuid(), uuid(),
                new BigDecimal("500.00"), "INR", "ref-1", Instant.now(), "corr-1");

        assertThat(outcome).isEqualTo(PersistOutcome.PERSISTED);
        verify(auditRepository).saveAndFlush(any(PaymentEventAudit.class));
    }

    @Test
    void persist_duplicateEventId_recognizedViaTheDatabaseConstraint_returnsDuplicate() {
        UUID eventId = uuid();
        when(auditRepository.saveAndFlush(any(PaymentEventAudit.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(auditRepository.existsById(eventId)).thenReturn(true);

        PersistOutcome outcome = service.persist(eventId, "PaymentSucceeded", uuid(), uuid(), uuid(),
                new BigDecimal("500.00"), "INR", "ref-1", Instant.now(), "corr-1");

        assertThat(outcome).isEqualTo(PersistOutcome.DUPLICATE);
    }

    @Test
    void persist_constraintViolationThatIsNotTheDuplicateEventId_isNotSwallowed_rethrown_remainsRetryable() {
        // "DB failure remains retryable" (Step 16 automated test #16):
        // existsById(eventId) says false, so this is NOT the expected
        // duplicate case; the original exception must propagate uncaught
        // so PaymentSucceededConsumer never acknowledges the message.
        UUID eventId = uuid();
        DataIntegrityViolationException original = new DataIntegrityViolationException("some other constraint");
        when(auditRepository.saveAndFlush(any(PaymentEventAudit.class))).thenThrow(original);
        when(auditRepository.existsById(eventId)).thenReturn(false);

        assertThatThrownBy(() -> service.persist(eventId, "PaymentSucceeded", uuid(), uuid(), uuid(),
                new BigDecimal("500.00"), "INR", "ref-1", Instant.now(), "corr-1"))
                .isSameAs(original);
    }

    @Test
    void persist_savesTheExactFieldsGiven() {
        UUID eventId = uuid();
        UUID paymentId = uuid();
        UUID bookingId = uuid();
        UUID userId = uuid();
        Instant occurredAt = Instant.parse("2026-09-28T10:00:00Z");
        PaymentEventAudit[] captured = new PaymentEventAudit[1];
        when(auditRepository.saveAndFlush(any(PaymentEventAudit.class))).thenAnswer(inv -> captured[0] = inv.getArgument(0));

        service.persist(eventId, "PaymentSucceeded", paymentId, bookingId, userId,
                new BigDecimal("1450.00"), "INR", "provider-ref-9", occurredAt, "corr-xyz");

        assertThat(captured[0].getEventId()).isEqualTo(eventId);
        assertThat(captured[0].getEventType()).isEqualTo("PaymentSucceeded");
        assertThat(captured[0].getPaymentId()).isEqualTo(paymentId);
        assertThat(captured[0].getBookingId()).isEqualTo(bookingId);
        assertThat(captured[0].getUserId()).isEqualTo(userId);
        assertThat(captured[0].getAmount()).isEqualByComparingTo("1450.00");
        assertThat(captured[0].getCurrency()).isEqualTo("INR");
        assertThat(captured[0].getProviderReference()).isEqualTo("provider-ref-9");
        assertThat(captured[0].getOccurredAt()).isEqualTo(occurredAt);
        assertThat(captured[0].getCorrelationId()).isEqualTo("corr-xyz");
    }

    @Test
    void persist_duplicate_doesNotAttemptASecondInsert() {
        UUID eventId = uuid();
        when(auditRepository.saveAndFlush(any(PaymentEventAudit.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(auditRepository.existsById(eventId)).thenReturn(true);

        service.persist(eventId, "PaymentSucceeded", uuid(), uuid(), uuid(),
                new BigDecimal("500.00"), "INR", "ref-1", Instant.now(), "corr-1");

        verify(auditRepository, never()).save(any());
    }
}
