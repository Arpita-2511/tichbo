package com.eventtick.audit.service;

import com.eventtick.audit.entity.BookingEventAudit;
import com.eventtick.audit.repository.BookingEventAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The one place a consumed {@code BookingCreated} event is turned into a
 * persisted row. Idempotent by construction (docs/architecture.md §49.5):
 * attempts the insert directly rather than checking existence first, then
 * catching the primary-key violation — checking-then-inserting has a
 * race (two redeliveries landing on two threads/instances at once could
 * both pass the check before either inserts), so the database's own
 * uniqueness constraint on {@code event_id} is the actual authority, not
 * an in-application {@code if (exists) return}.
 *
 * <p>{@link #persist} is a plain method, not itself {@code @Transactional}:
 * {@code auditRepository.saveAndFlush(...)} is an inherited {@code
 * SimpleJpaRepository} method, which already carries its own {@code
 * @Transactional(propagation = REQUIRED)} — each call commits (or rolls
 * back) on its own. This mirrors {@code PaymentService.createPayment}'s
 * own {@code recoverFromInsertRace} precedent exactly: catching a
 * constraint violation immediately around one {@code saveAndFlush} call,
 * not inside a broader hand-rolled {@code @Transactional} method whose
 * Hibernate session could otherwise be left unusable by the failed flush.
 */
@Service
public class BookingEventAuditService {

    private static final Logger log = LoggerFactory.getLogger(BookingEventAuditService.class);

    private final BookingEventAuditRepository auditRepository;

    public BookingEventAuditService(BookingEventAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * @throws DataIntegrityViolationException if the insert fails for a
     *         reason other than the expected {@code event_id} duplicate —
     *         a genuine, unexpected failure, not silently treated as a
     *         duplicate (docs/architecture.md §49.5's explicit requirement).
     *         Propagates to the caller uncaught; {@code
     *         BookingCreatedConsumer} does not acknowledge in that case.
     */
    public PersistOutcome persist(UUID eventId, String eventType, UUID bookingId, UUID userId, UUID showId,
                                   Instant occurredAt, String correlationId) {
        BookingEventAudit row = new BookingEventAudit(eventId, eventType, bookingId, userId, showId,
                occurredAt, correlationId);
        try {
            auditRepository.saveAndFlush(row);
            return PersistOutcome.PERSISTED;
        } catch (DataIntegrityViolationException ex) {
            if (auditRepository.existsById(eventId)) {
                // Re-queried, not assumed — the same disambiguation
                // discipline PaymentService.recoverFromInsertRace already
                // uses: confirm event_id is genuinely what already exists
                // before treating this as a safe, ignorable duplicate.
                log.info("event {} already recorded — duplicate delivery, ignoring", eventId);
                return PersistOutcome.DUPLICATE;
            }
            // The insert failed for some other reason (e.g. a NOT NULL
            // violation that validation upstream should have caught, or an
            // unrelated database problem) — this is not a duplicate and
            // must not be treated as one. Rethrown so the consumer does
            // NOT acknowledge; the message remains retryable.
            log.error("unexpected constraint violation persisting event {} — not treating as a duplicate", eventId, ex);
            throw ex;
        }
    }
}
