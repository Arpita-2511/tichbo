package com.eventtick.payment.scheduler;

import com.eventtick.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin, internal-only trigger for {@link PaymentService}'s two lifecycle
 * sweeps (Phase 15 Step 3, docs/architecture.md §25.3) — no public
 * endpoint, no admin dashboard, matching the explicit "prefer an internal
 * scheduled mechanism over exposing a public maintenance endpoint"
 * instruction. Plain Spring {@code @Scheduled} (already part of
 * spring-context, no new dependency) — no new library, no Kafka, no
 * external job scheduler; this project has no multi-instance deployment
 * yet, and the sweeps themselves (not this class) are what's actually
 * safe to run repeatedly or from more than one instance, via database
 * state alone — see {@link PaymentService#expirePendingPayments}/
 * {@link PaymentService#reconcilePendingBookingSync}'s own Javadoc.
 *
 * <p>{@code fixedDelay} (not {@code fixedRate}): the next run starts this
 * many milliseconds after the previous run <i>finishes</i>, so a slow
 * sweep (e.g. booking-service responding slowly) can never overlap with
 * itself.
 */
@Component
public class PaymentLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentLifecycleScheduler.class);

    private final PaymentService paymentService;

    public PaymentLifecycleScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${payment.expiration-sweep-interval-ms}")
    public void sweepExpiredPayments() {
        int expired = paymentService.expirePendingPayments();
        if (expired > 0) {
            log.info("expiration sweep: expired {} payment(s)", expired);
        }
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation-sweep-interval-ms}")
    public void sweepPendingReconciliation() {
        int reconciled = paymentService.reconcilePendingBookingSync();
        if (reconciled > 0) {
            log.info("reconciliation sweep: synced {} payment(s) with booking-service", reconciled);
        }
    }
}
