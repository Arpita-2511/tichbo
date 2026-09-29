package com.eventtick.payment.repository;

import com.eventtick.payment.entity.BookingSyncStatus;
import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Payment} (the {@code payments}
 * table).
 *
 * <p>{@code @Transactional} on {@link #expireIfStillPending} and
 * {@link #markBookingSynced}: unlike inherited CRUD methods (save, delete,
 * ...), which {@code SimpleJpaRepository} already wraps transactionally,
 * a custom {@code @Modifying @Query} method is <b>not</b> auto-wrapped by
 * Spring Data JPA — calling one with no ambient transaction throws
 * {@code TransactionRequiredException} at the Hibernate level. Both of
 * {@code PaymentService}'s Phase 15 Step 3 sweep methods deliberately call
 * these with no ambient transaction of their own (see that class's
 * Javadoc), so each method needs its own, scoped to exactly that one write
 * — never spanning the surrounding booking-service HTTP call.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Idempotency lookup — {@code idempotency_key} is unique
     * (uq_payments_idempotency_key), so at most one row can ever match.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * An application-level pre-check for "does this booking already have a
     * live payment" (see {@link PaymentStatus#isLive()}) — an early,
     * friendlier rejection before ever attempting an insert. The real,
     * race-condition-safe guarantee is
     * {@code uq_payments_one_active_per_booking} (a partial unique index),
     * not this query; see {@code PaymentService} for how a
     * {@code DataIntegrityViolationException} from that index is handled.
     */
    @Query("SELECT p FROM Payment p WHERE p.bookingId = :bookingId AND p.status IN :liveStatuses")
    Optional<Payment> findLiveByBookingId(@Param("bookingId") UUID bookingId, @Param("liveStatuses") List<PaymentStatus> liveStatuses);

    /**
     * The booking-scoped lookup behind {@code GET /api/payments?bookingId=}
     * — most recent payment for a booking (there may be several historical
     * terminal-and-failed ones plus at most one live one), most-recent
     * first so the single live/most-relevant one sorts first.
     */
    List<Payment> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Candidates for the expiration sweep (Phase 15 Step 3) — payments
     * that have been {@code PENDING} since before {@code cutoff}. The
     * actual transition is a separate, conditional step
     * ({@link #expireIfStillPending}) so a payment resolved by the normal
     * creation flow between this read and that write is never
     * double-processed.
     */
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant cutoff);

    /**
     * Atomically transitions one payment {@code PENDING -> EXPIRED},
     * conditioned on it still being {@code PENDING} at the moment of the
     * update — the database, not this application, is what makes this
     * safe to call repeatedly or from multiple instances: the
     * {@code WHERE status = 'PENDING'} guard means only the first caller
     * to actually reach the database wins, and every other concurrent
     * attempt (including a re-run of the sweep, or another app instance)
     * affects zero rows instead of throwing or double-transitioning.
     *
     * @return 1 if this call actually expired the payment, 0 if it had
     *         already moved out of {@code PENDING} by the time this ran
     */
    @Transactional
    @Modifying
    @Query("UPDATE Payment p SET p.status = com.eventtick.payment.entity.PaymentStatus.EXPIRED "
            + "WHERE p.id = :id AND p.status = com.eventtick.payment.entity.PaymentStatus.PENDING")
    int expireIfStillPending(@Param("id") UUID id);

    /**
     * Reconciliation candidates (Phase 15 Step 3, scenario G): terminal
     * payments whose booking-service side effect hasn't been acknowledged
     * yet. See {@link BookingSyncStatus} and
     * {@code database/migrations/0012_add_booking_sync_status_to_payments}.
     */
    List<Payment> findByStatusInAndBookingSyncStatus(List<PaymentStatus> statuses, BookingSyncStatus bookingSyncStatus);

    /**
     * Marks a payment's booking-service side effect as acknowledged —
     * called only after {@code BookingServiceClient} actually confirms
     * success, never speculatively.
     */
    @Transactional
    @Modifying
    @Query("UPDATE Payment p SET p.bookingSyncStatus = com.eventtick.payment.entity.BookingSyncStatus.DONE WHERE p.id = :id")
    void markBookingSynced(@Param("id") UUID id);
}
