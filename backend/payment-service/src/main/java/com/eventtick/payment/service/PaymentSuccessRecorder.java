package com.eventtick.payment.service;

import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.event.EventTopics;
import com.eventtick.payment.event.PaymentSucceededPayload;
import com.eventtick.payment.outbox.PaymentOutboxService;
import com.eventtick.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place a payment's {@code SUCCESS} row and its {@code
 * PaymentSucceeded} outbox row are written atomically — Phase 16 Step 4's
 * critical transaction-boundary requirement (docs/architecture.md §50.4):
 * the outbox row must commit in the SAME database transaction as the
 * status write, so the two can never disagree.
 *
 * <p><b>Why this is its own bean, not just another private method on
 * {@link PaymentService}:</b> Spring's {@code @Transactional} is
 * proxy-based — it only takes effect on a call that actually goes through
 * the bean's proxy. {@code PaymentService.chargeAndResolve} is reached via
 * {@code this.chargeAndResolve(...)} from {@code createPayment}, i.e.
 * self-invocation, which bypasses the proxy entirely; annotating anything
 * inside that call chain with {@code @Transactional} would silently do
 * nothing (a well-known Spring pitfall, not a hypothetical one). Injecting
 * this as a *separate* collaborator bean and calling {@link #recordSuccess}
 * on it from {@code chargeAndResolve} is a genuine external call through
 * this class's own proxy, so {@code @Transactional} actually applies.
 *
 * <p>Deliberately does <b>not</b> call {@code BookingServiceClient} or
 * touch {@code bookingSyncStatus} — those happen in {@code PaymentService}
 * only after this method returns (i.e. only after the transaction below
 * has already committed), exactly matching the existing, unmodified
 * payment→booking confirmation mechanism (docs/architecture.md §25.3):
 * that HTTP call must never be inside a database transaction, and this
 * step does not change that.
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
}
