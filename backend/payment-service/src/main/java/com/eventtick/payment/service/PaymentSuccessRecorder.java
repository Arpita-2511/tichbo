package com.eventtick.payment.service;

import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.event.EventTopics;
import com.eventtick.payment.event.PaymentExpiredPayload;
import com.eventtick.payment.event.PaymentFailedPayload;
import com.eventtick.payment.event.PaymentSucceededPayload;
import com.eventtick.payment.outbox.PaymentOutboxService;
import com.eventtick.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place a payment's terminal status write and the matching
 * outbox row are written atomically — Phase 16 Step 4's critical
 * transaction-boundary requirement (docs/architecture.md §50.4), now
 * covering all three terminal outcomes a payment can reach via {@link
 * PaymentService#chargeAndResolve}/{@link PaymentService#expirePendingPayments}
 * (Step 5): {@link #recordSuccess} ({@code SUCCESS} →
 * {@code PaymentSucceeded}), {@link #recordFailure} ({@code FAILED} →
 * {@code PaymentFailed}), and {@link #recordExpiryIfStillPending}
 * ({@code PENDING -> EXPIRED} → {@code PaymentExpired}). In every case the
 * status write and the outbox row commit in the SAME database transaction,
 * so the two can never disagree — kept on this one class (not three) since
 * all three share the identical atomicity requirement and the identical
 * reason they need to be a separate bean at all (below), and splitting
 * them into separate classes would just be the same mechanism copy-pasted
 * three times, not a different design.
 *
 * <p><b>Why this is its own bean, not just another private method on
 * {@link PaymentService}:</b> Spring's {@code @Transactional} is
 * proxy-based — it only takes effect on a call that actually goes through
 * the bean's proxy. {@code PaymentService.chargeAndResolve}/
 * {@code expirePendingPayments} are reached via {@code this.foo(...)} from
 * {@code createPayment}/their own public entry point, i.e. self-invocation,
 * which bypasses the proxy entirely; annotating anything inside that call
 * chain with {@code @Transactional} would silently do nothing (a
 * well-known Spring pitfall, not a hypothetical one). Injecting this as a
 * *separate* collaborator bean and calling one of its methods from
 * {@code PaymentService} is a genuine external call through this class's
 * own proxy, so {@code @Transactional} actually applies.
 *
 * <p>Deliberately does <b>not</b> call {@code BookingServiceClient} or
 * touch {@code bookingSyncStatus} for any of the three methods — that
 * HTTP call happens in {@code PaymentService} only after the relevant
 * method here returns (i.e. only after its transaction has already
 * committed), exactly matching the existing, unmodified payment→booking
 * confirmation/release mechanism (docs/architecture.md §25.3): it must
 * never be inside a database transaction, and this step does not change
 * that for any of the three outcomes.
 */
@Service
public class PaymentSuccessRecorder {

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxService paymentOutboxService;

    public PaymentSuccessRecorder(PaymentRepository paymentRepository, PaymentOutboxService paymentOutboxService) {
        this.paymentRepository = paymentRepository;
        this.paymentOutboxService = paymentOutboxService;
    }

    /**
     * @param payment an in-memory {@link Payment} already transitioned to
     *                {@code SUCCESS} with its {@code providerReference}
     *                set (validated and mutated by {@code PaymentService}
     *                via the centralized {@code PaymentStatus} state
     *                machine before this is ever called) — not yet saved.
     * @param correlationId the originating request's own X-Request-ID —
     *                      see {@link PaymentOutboxService#record}
     * @return the saved, now-persisted {@link Payment}
     */
    @Transactional
    public Payment recordSuccess(Payment payment, String correlationId) {
        Payment saved = paymentRepository.save(payment);

        PaymentSucceededPayload payload = new PaymentSucceededPayload(
                saved.getId(), saved.getBookingId(), saved.getUserId(),
                saved.getAmount(), saved.getCurrency(), saved.getProviderReference());
        paymentOutboxService.record("PaymentSucceeded", 1, "Payment", saved.getId(),
                EventTopics.PAYMENT, payload, correlationId, null);

        return saved;
    }

    /**
     * Phase 16 Step 5. Mirrors {@link #recordSuccess} exactly, for the
     * {@code FAILED} outcome.
     *
     * @param payment an in-memory {@link Payment} already transitioned to
     *                {@code FAILED} with its {@code providerReference} set
     *                (same precondition as {@link #recordSuccess}) — not
     *                yet saved.
     * @param correlationId the originating request's own X-Request-ID
     * @param failureReason {@link com.eventtick.payment.provider.PaymentProviderResult#failureReason()}
     *                      from the same charge attempt — never persisted
     *                      on {@link Payment} itself, only carried into
     *                      this event (see {@link PaymentFailedPayload}'s
     *                      own Javadoc for why)
     * @return the saved, now-persisted {@link Payment}
     */
    @Transactional
    public Payment recordFailure(Payment payment, String correlationId, String failureReason) {
        Payment saved = paymentRepository.save(payment);

        PaymentFailedPayload payload = new PaymentFailedPayload(
                saved.getId(), saved.getBookingId(), saved.getUserId(),
                saved.getAmount(), saved.getCurrency(), saved.getProviderReference(), failureReason);
        paymentOutboxService.record("PaymentFailed", 1, "Payment", saved.getId(),
                EventTopics.PAYMENT, payload, correlationId, null);

        return saved;
    }

    /**
     * Phase 16 Step 5. The {@code EXPIRED} counterpart to {@link
     * #recordSuccess}/{@link #recordFailure} — structurally different from
     * both because the underlying transition itself is different:
     * {@link PaymentRepository#expireIfStillPending} is a conditional
     * {@code UPDATE ... WHERE status = 'PENDING'}, not a plain entity
     * {@code save}, and it already carries its own {@code @Transactional}
     * (required because a custom {@code @Modifying @Query} method is not
     * auto-wrapped the way inherited {@code save} is — see that
     * repository's own Javadoc). Calling it from inside <i>this</i>
     * method's {@code @Transactional} does not start a second, independent
     * transaction — Spring's default {@code REQUIRED} propagation makes it
     * join this one — so the conditional expiry and the outbox write below
     * still commit or roll back together, exactly like the other two
     * methods.
     *
     * <p>The conditional guard is what makes this safe to call for a
     * {@code candidate} that a concurrent sweep (or the normal creation
     * flow) already resolved between the caller's own read and this call:
     * if the update affects zero rows, nothing — including the outbox
     * write — happens, and this returns {@code false}.
     *
     * @param candidate a {@link Payment} read as {@code PENDING} by the
     *                   caller's own candidate query (not re-read here);
     *                   its {@code id}/{@code bookingId}/{@code userId}/
     *                   {@code amount}/{@code currency} are used for the
     *                   event payload regardless of whether this call
     *                   actually wins the race below — none of those
     *                   fields change between {@code PENDING} and
     *                   {@code EXPIRED}
     * @param correlationId the originating request's own X-Request-ID, or
     *                      blank/{@code null} for this sweep's own
     *                      background trigger (no originating request) —
     *                      {@link PaymentOutboxService#record} generates a
     *                      fresh one, exactly the fallback its own Javadoc
     *                      already documents for this case
     * @return {@code true} if this call actually expired the payment (and
     *         therefore recorded the event); {@code false} if it had
     *         already moved out of {@code PENDING} by the time this ran
     */
    @Transactional
    public boolean recordExpiryIfStillPending(Payment candidate, String correlationId) {
        int updated = paymentRepository.expireIfStillPending(candidate.getId());
        if (updated != 1) {
            return false;
        }

        PaymentExpiredPayload payload = new PaymentExpiredPayload(
                candidate.getId(), candidate.getBookingId(), candidate.getUserId(),
                candidate.getAmount(), candidate.getCurrency());
        paymentOutboxService.record("PaymentExpired", 1, "Payment", candidate.getId(),
                EventTopics.PAYMENT, payload, correlationId, null);
        return true;
    }
}
