package com.eventtick.payment.service;

import com.eventtick.payment.client.BookingServiceClient;
import com.eventtick.payment.client.BookingSummary;
import com.eventtick.payment.entity.BookingSyncStatus;
import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;
import com.eventtick.payment.exception.IdempotencyConflictException;
import com.eventtick.payment.exception.InvalidPaymentStateException;
import com.eventtick.payment.exception.PaymentConflictException;
import com.eventtick.payment.exception.PaymentNotFoundException;
import com.eventtick.payment.exception.PaymentOwnershipException;
import com.eventtick.payment.provider.PaymentChargeRequest;
import com.eventtick.payment.provider.PaymentProvider;
import com.eventtick.payment.provider.PaymentProviderResult;
import com.eventtick.payment.repository.PaymentRepository;
import com.eventtick.payment.security.PaymentRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment creation, lookup, and lifecycle (expiration + reconciliation) —
 * see docs/architecture.md §25.1 for the original design and §25.3 for
 * Step 3's additions. Deliberately does <b>not</b> wrap any multi-step flow
 * that includes a booking-service HTTP call in one {@code @Transactional}
 * method: payment and booking are separate service domains reached over
 * HTTP, and no distributed transaction is used or claimed anywhere in this
 * class — each durable step (a payment row's own status transition) is
 * committed on its own (via {@link PaymentRepository}'s individually
 * transactional methods), and every booking-service call is best-effort,
 * with its outcome recorded in {@code Payment.bookingSyncStatus} rather
 * than thrown back as a payment failure (see {@link #recordSyncOutcome}).
 *
 * <h2>What Step 3 adds vs. still defers</h2>
 * Adds: the {@code EXPIRED} transition (a scheduled sweep, see
 * {@link #expirePendingPayments}), centralized transition validation (see
 * {@link PaymentStatus#canTransitionTo}), and the scenario-G/expiration
 * reconciliation mechanism (see {@link #reconcilePendingBookingSync}).
 * <b>Still defers</b> (see §25.3's open questions): a customer-facing
 * "abandon checkout" action that would actually reach {@code CREATED ->
 * CANCELLED} in production use (the transition is valid in the state
 * machine and tested directly, but nothing calls it yet — no new endpoint
 * was added, since that's a customer-facing feature decision beyond this
 * step's scope), and a real payment provider/webhook.
 *
 * <h2>Phase 16 Step 4/5 additions</h2>
 * A successful charge enqueues a {@code PaymentSucceeded} outbox event
 * (Step 4); a failed charge enqueues {@code PaymentFailed}, and an
 * expiring payment enqueues {@code PaymentExpired} (Step 5) — all three
 * atomically with their respective status write, but that atomic write
 * happens in a separate bean, {@link PaymentSuccessRecorder} (despite its
 * name — see that class's own Javadoc for why all three live there), not
 * here, precisely because this class's own entry points must stay
 * un-{@code @Transactional} for the HTTP-spanning reasons above. This is
 * an addition, not a replacement: {@code bookingSyncStatus}, {@link
 * #recordSyncOutcome}, and every existing payment→booking consistency
 * mechanism above are unchanged. Kafka remains a purely asynchronous side
 * channel — see docs/architecture.md §50/§53.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Statuses {@link PaymentRepository#findLiveByBookingId} treats as "still live" — see {@link PaymentStatus#isLive()}. */
    private static final List<PaymentStatus> LIVE_STATUSES = List.of(PaymentStatus.CREATED, PaymentStatus.PENDING, PaymentStatus.SUCCESS);

    /** Terminal statuses that have a booking-service side effect worth reconciling — see {@link #reconcilePendingBookingSync}. */
    private static final List<PaymentStatus> RECONCILIATION_STATUSES = List.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED, PaymentStatus.EXPIRED);

    private final PaymentRepository paymentRepository;
    private final BookingServiceClient bookingServiceClient;
    private final PaymentProvider paymentProvider;
    private final PaymentSuccessRecorder paymentSuccessRecorder;
    private final String defaultCurrency;
    private final Duration expirationDuration;

    public PaymentService(PaymentRepository paymentRepository,
                           BookingServiceClient bookingServiceClient,
                           PaymentProvider paymentProvider,
                           PaymentSuccessRecorder paymentSuccessRecorder,
                           @Value("${payment.default-currency}") String defaultCurrency,
                           @Value("${payment.expiration-minutes}") long expirationMinutes) {
        this.paymentRepository = paymentRepository;
        this.bookingServiceClient = bookingServiceClient;
        this.paymentProvider = paymentProvider;
        this.paymentSuccessRecorder = paymentSuccessRecorder;
        this.defaultCurrency = defaultCurrency;
        this.expirationDuration = Duration.ofMinutes(expirationMinutes);
    }

    /**
     * @param callerUserId the authenticated caller's own id (from the JWT — never client-supplied)
     * @param bookingId    the booking to pay for
     * @param idempotencyKey client-supplied idempotency key
     * @param correlationId the originating request's own X-Request-ID
     *                      (Phase 16 Step 4) — carried onto a {@code
     *                      PaymentSucceeded} event if this call actually
     *                      reaches SUCCESS; see {@link
     *                      com.eventtick.payment.outbox.PaymentOutboxService#record}
     *                      for what happens if it's blank
     * @return the resulting payment, and whether this call actually created it (vs. replaying an existing one) —
     *         the controller uses that to choose 201 vs. 200.
     */
    public PaymentCreationResult createPayment(UUID callerUserId, UUID bookingId, String idempotencyKey,
                                                String correlationId) {
        Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            Payment existing = existingByKey.get();
            if (!existing.getBookingId().equals(bookingId)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            // Same key, same booking: a genuine replay — return the existing
            // result rather than re-charging (§25.1 idempotency design).
            // No PaymentSucceeded event either: that event describes the
            // NOT SUCCESS -> SUCCESS transition, which already happened (and
            // was already published) on the original call — see
            // docs/architecture.md §50.6.
            return new PaymentCreationResult(existing, false);
        }

        BookingSummary booking = bookingServiceClient.getBooking(bookingId);
        if (!booking.userId().equals(callerUserId)) {
            throw new PaymentOwnershipException(bookingId);
        }
        if (!booking.isPending()) {
            throw PaymentConflictException.bookingNotPayable(bookingId, booking.status());
        }

        Optional<Payment> live = paymentRepository.findLiveByBookingId(bookingId, LIVE_STATUSES);
        if (live.isPresent()) {
            // A concurrent request carrying this same key can insert its row
            // between the key lookup above and this check — that is a replay
            // (FR-46), not a conflicting second payment.
            if (idempotencyKey.equals(live.get().getIdempotencyKey())) {
                return new PaymentCreationResult(live.get(), false);
            }
            throw PaymentConflictException.duplicateLivePayment(bookingId);
        }

        Payment payment = new Payment();
        payment.setBookingId(bookingId);
        payment.setUserId(callerUserId);
        payment.setAmount(booking.totalAmount());
        payment.setCurrency(defaultCurrency);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setProvider(paymentProvider.id());
        payment.setIdempotencyKey(idempotencyKey);

        Payment saved;
        try {
            saved = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request won the race between our pre-checks above
            // and this insert — uq_payments_idempotency_key or
            // uq_payments_one_active_per_booking fired. Re-check by key
            // first (the more specific, more common race); otherwise it's
            // the one-live-payment-per-booking index.
            return recoverFromInsertRace(bookingId, idempotencyKey, ex);
        }

        return new PaymentCreationResult(chargeAndResolve(saved, correlationId), true);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(() -> PaymentNotFoundException.byId(paymentId));
    }

    @Transactional(readOnly = true)
    public Payment getPaymentForBooking(UUID bookingId) {
        List<Payment> payments = paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
        if (payments.isEmpty()) {
            throw PaymentNotFoundException.forBooking(bookingId);
        }
        return payments.get(0);
    }

    /** {@code true} if the caller is this payment's owner or an admin — the one ownership rule every GET endpoint uses. */
    public boolean canView(Payment payment, UUID callerUserId, PaymentRole callerRole) {
        return callerRole == PaymentRole.ADMIN || payment.getUserId().equals(callerUserId);
    }

    // ─── internal helpers ───────────────────────────────────────────────

    /**
     * Transitions a freshly-created ({@code CREATED}) payment through the
     * provider call and persists the terminal-for-now result. Not wrapped
     * in the same transaction as the insert above — the {@code CREATED}
     * row is already durably committed before the provider is ever called,
     * so a provider failure/timeout never leaves an uncommitted payment.
     *
     * <p>Phase 16 Step 4: on a successful charge, the {@code SUCCESS}
     * status write and the {@code PaymentSucceeded} outbox row are
     * committed atomically by {@link PaymentSuccessRecorder#recordSuccess}
     * (a separate bean — see that class's own Javadoc for why it must be)
     * — still strictly before {@code bookingServiceClient.confirmBooking}
     * is ever called, so a Kafka-unrelated concern (the outbox write)
     * never delays or risks the existing, unmodified booking-confirmation
     * call. If Kafka itself is unreachable, none of this changes: {@link
     * com.eventtick.payment.outbox.PaymentOutboxService#record} only
     * writes a database row.
     */
    private Payment chargeAndResolve(Payment payment, String correlationId) {
        transitionTo(payment, PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        PaymentProviderResult result = paymentProvider.charge(
                new PaymentChargeRequest(payment.getId(), payment.getAmount(), payment.getCurrency(), payment.getIdempotencyKey()));

        if (result.success()) {
            transitionTo(payment, PaymentStatus.SUCCESS);
            payment.setProviderReference(result.providerReference());
            payment = paymentSuccessRecorder.recordSuccess(payment, correlationId);
            recordSyncOutcome(payment, bookingServiceClient.confirmBooking(payment.getBookingId()));
        } else {
            transitionTo(payment, PaymentStatus.FAILED);
            payment.setProviderReference(result.providerReference());
            payment = paymentSuccessRecorder.recordFailure(payment, correlationId, result.failureReason());
            log.info("payment failed id={} booking={} reason={}", payment.getId(), payment.getBookingId(), result.failureReason());
            recordSyncOutcome(payment, bookingServiceClient.releaseBooking(payment.getBookingId()));
        }
        return payment;
    }

    /** Validates the transition via the centralized state machine, then applies it in-memory (caller persists). */
    private void transitionTo(Payment payment, PaymentStatus target) {
        if (!payment.getStatus().canTransitionTo(target)) {
            throw new InvalidPaymentStateException(payment.getId(), payment.getStatus(), target);
        }
        payment.setStatus(target);
    }

    /**
     * Records whether booking-service actually acknowledged this payment's
     * terminal side effect. Only ever moves {@code PENDING -> DONE} — a
     * failed attempt leaves the default {@code PENDING} untouched (already
     * persisted at row-creation time), so {@link #reconcilePendingBookingSync}
     * finds it later without this method needing to write anything on
     * failure.
     */
    private void recordSyncOutcome(Payment payment, boolean bookingServiceAcknowledged) {
        if (bookingServiceAcknowledged) {
            paymentRepository.markBookingSynced(payment.getId());
            payment.setBookingSyncStatus(BookingSyncStatus.DONE);
        }
    }

    /**
     * Phase 15 Step 3 expiration sweep: finds payments {@code PENDING}
     * since before {@code payment.expiration-minutes} ago and transitions
     * each to {@code EXPIRED}, then best-effort releases the booking (the
     * same {@code releaseBooking} call a {@code FAILED} payment makes).
     *
     * <p><b>Idempotent and safe to call repeatedly or from multiple
     * instances</b>: {@link PaymentRepository#expireIfStillPending} is a
     * single conditional {@code UPDATE ... WHERE status = 'PENDING'} — the
     * database's own row-level locking is what actually prevents two
     * concurrent sweeps (or a sweep racing the normal creation flow's own
     * {@code PENDING -> SUCCESS/FAILED} transition) from both "winning";
     * whichever transaction's {@code UPDATE} commits first is the only one
     * that affects a row, every other caller sees 0 rows updated and moves
     * on without erroring. Called on a schedule by
     * {@code PaymentLifecycleScheduler}, but is a plain public method
     * specifically so tests can call it directly rather than waiting on
     * a timer.
     *
     * @return how many payments this call actually expired
     */
    public int expirePendingPayments() {
        Instant cutoff = Instant.now().minus(expirationDuration);
        List<Payment> candidates = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff);
        int expiredCount = 0;
        for (Payment candidate : candidates) {
            // Phase 16 Step 5: the conditional expiry itself and the
            // PaymentExpired outbox row now commit atomically — see
            // PaymentSuccessRecorder#recordExpiryIfStillPending. No
            // originating request exists for this scheduled sweep, so
            // correlationId is null; PaymentOutboxService#record's own
            // documented fallback generates a fresh one.
            if (paymentSuccessRecorder.recordExpiryIfStillPending(candidate, null)) {
                expiredCount++;
                log.info("payment expired id={} booking={} (pending since {})", candidate.getId(), candidate.getBookingId(), candidate.getCreatedAt());
                recordSyncOutcome(candidate, bookingServiceClient.releaseBooking(candidate.getBookingId()));
            }
            // else: already resolved (SUCCESS/FAILED) by the normal flow
            // between this read and the conditional update above — not an
            // error, just nothing left for this sweep to do for that row.
        }
        return expiredCount;
    }

    /**
     * Phase 15 Step 3 reconciliation sweep — the scenario-G mechanism: for
     * every terminal payment (SUCCESS/FAILED/EXPIRED) whose booking-service
     * side effect was never acknowledged (see {@link BookingSyncStatus}),
     * retries exactly that call (confirm for SUCCESS, release for
     * FAILED/EXPIRED). Never touches the payment's own {@code status} —
     * that remains this service's authoritative, already-committed record
     * regardless of how many times reconciliation retries the booking
     * side. Safe across restarts: the candidate set comes entirely from
     * {@code payments.booking_sync_status}, not any in-memory queue.
     * Retrying a successful confirm/cancel is itself safe only because
     * booking-service's own {@code confirmBooking}/{@code cancelBooking}
     * were made idempotent on an already-terminal booking (Phase 15 Step
     * 3's one booking-service change) — this method does not duplicate a
     * booking or a payment either way.
     *
     * @return how many payments this call successfully reconciled
     */
    public int reconcilePendingBookingSync() {
        List<Payment> candidates = paymentRepository.findByStatusInAndBookingSyncStatus(RECONCILIATION_STATUSES, BookingSyncStatus.PENDING);
        int reconciledCount = 0;
        for (Payment candidate : candidates) {
            boolean acknowledged = candidate.getStatus() == PaymentStatus.SUCCESS
                    ? bookingServiceClient.confirmBooking(candidate.getBookingId())
                    : bookingServiceClient.releaseBooking(candidate.getBookingId());

            if (acknowledged) {
                paymentRepository.markBookingSynced(candidate.getId());
                reconciledCount++;
                log.info("reconciliation: booking sync completed for payment={} booking={} status={}",
                        candidate.getId(), candidate.getBookingId(), candidate.getStatus());
            } else {
                log.warn("reconciliation: booking sync still failing for payment={} booking={} status={} — will retry next sweep",
                        candidate.getId(), candidate.getBookingId(), candidate.getStatus());
            }
        }
        return reconciledCount;
    }

    private PaymentCreationResult recoverFromInsertRace(UUID bookingId, String idempotencyKey, DataIntegrityViolationException ex) {
        Optional<Payment> byKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (byKey.isPresent()) {
            Payment existing = byKey.get();
            if (!existing.getBookingId().equals(bookingId)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return new PaymentCreationResult(existing, false);
        }
        // Not an idempotency-key collision, so it must be the one-live-
        // payment-per-booking index — someone else's request for the same
        // booking won the race.
        log.info("payment insert race for booking={} resolved as duplicate-live-payment: {}", bookingId, ex.getMostSpecificCause().toString());
        throw PaymentConflictException.duplicateLivePayment(bookingId);
    }

    /** Whether {@link #createPayment} actually created a new payment (201) or replayed an existing one (200). */
    public record PaymentCreationResult(Payment payment, boolean created) {
    }
}
