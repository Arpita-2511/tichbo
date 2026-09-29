package com.eventtick.audit.service;

import com.eventtick.audit.entity.PaymentEventAudit;
import com.eventtick.audit.repository.PaymentEventAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The one place a consumed {@code PaymentSucceeded} event is turned into a
 * persisted row — mirrors {@link BookingEventAuditService} exactly,
 * including the insert-then-catch idempotency discipline (docs/
 * architecture.md §49.5/§50.9): the database's own uniqueness constraint
 * on {@code event_id} is the actual authority, not an in-application
 * {@code if (exists) return}, since checking-then-inserting has a race two
 * redeliveries landing on two threads/instances at once could both pass.
 */
@Service
public class PaymentEventAuditService {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventAuditService.class);

    private final PaymentEventAuditRepository auditRepository;

    public PaymentEventAuditService(PaymentEventAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * @throws DataIntegrityViolationException if the insert fails for a
     *         reason other than the expected {@code event_id} duplicate —
     *         propagates to the caller uncaught; {@code
     *         PaymentSucceededConsumer} does not acknowledge in that case.
     */
    public PersistOutcome persist(UUID eventId, String eventType, UUID paymentId, UUID bookingId, UUID userId,
                                   BigDecimal amount, String currency, String providerReference,
                                   Instant occurredAt, String correlationId) {
        PaymentEventAudit row = new PaymentEventAudit(eventId, eventType, paymentId, bookingId, userId,
                amount, currency, providerReference, occurredAt, correlationId);
        try {
            auditRepository.saveAndFlush(row);
            return PersistOutcome.PERSISTED;
        } catch (DataIntegrityViolationException ex) {
            if (auditRepository.existsById(eventId)) {
                // Re-queried, not assumed — same disambiguation discipline
                // as BookingEventAuditService#persist.
                log.info("event {} already recorded — duplicate delivery, ignoring", eventId);
                return PersistOutcome.DUPLICATE;
            }
            log.error("unexpected constraint violation persisting event {} — not treating as a duplicate", eventId, ex);
            throw ex;
        }
    }
}
