package com.eventtick.payment.service;

import com.eventtick.payment.client.BookingServiceClient;
import com.eventtick.payment.entity.BookingSyncStatus;
import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;
import com.eventtick.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 15 Step 3: {@link PaymentService#expirePendingPayments()} against a
 * real H2-backed {@link PaymentRepository} (same {@code @SpringBootTest}
 * style as {@code PaymentRepositoryConstraintTest}), with
 * {@link BookingServiceClient} mocked out (same style as
 * {@code PaymentControllerTest}) since only the expiration mechanism itself
 * — not booking-service's HTTP behavior, already covered by
 * {@code BookingServiceClientTest} — is under test here.
 *
 * <p>{@code created_at} is {@code insertable = false} (database-owned —
 * see {@link Payment}'s Javadoc), so {@link #backdate} uses a raw native
 * update via {@link EntityManager}, test-only, to simulate "created N
 * minutes ago" without needing to actually wait.
 *
 * <p>{@code payment.expiration-minutes} is 15 in
 * {@code src/test/resources/application.yml} — every test below backdates
 * relative to that real, injected value rather than hardcoding "15".
 */
@SpringBootTest
class PaymentExpirationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private BookingServiceClient bookingServiceClient;

    /**
     * Each test's own aggregate-count assertions (e.g. "exactly 1 expired")
     * only hold if the {@code payments} table starts empty — this is a
     * plain {@code @SpringBootTest} (not {@code @Transactional}, since the
     * production code under test deliberately relies on each repository
     * call opening/closing its own persistence context — see
     * {@code PaymentService#expirePendingPayments}'s own Javadoc — so
     * wrapping the whole test method in one transaction would reintroduce
     * exactly the stale-managed-entity problem that design avoids). Cleanup
     * between tests instead of relying on transactional rollback.
     */
    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAll();
    }

    private static Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setBookingId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("INR");
        payment.setStatus(status);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey("expiry-test-" + UUID.randomUUID());
        return payment;
    }

    /** Rewrites {@code created_at} directly in the database, bypassing the entity's {@code insertable = false}. */
    private void backdate(UUID paymentId, Instant createdAt) {
        jdbcTemplate.update("UPDATE payments SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), paymentId);
    }

    @Test
    void pendingPayment_olderThanExpirationWindow_isExpired() {
        Payment saved = paymentRepository.saveAndFlush(payment(PaymentStatus.PENDING));
        backdate(saved.getId(), Instant.now().minus(20, ChronoUnit.MINUTES));
        when(bookingServiceClient.releaseBooking(any())).thenReturn(true);

        int expiredCount = paymentService.expirePendingPayments();

        assertThat(expiredCount).isEqualTo(1);
        Payment reloaded = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void pendingPayment_withinExpirationWindow_isNotExpired() {
        Payment saved = paymentRepository.saveAndFlush(payment(PaymentStatus.PENDING));
        backdate(saved.getId(), Instant.now().minus(2, ChronoUnit.MINUTES));

        int expiredCount = paymentService.expirePendingPayments();

        Payment reloaded = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(expiredCount).isZero();
        verify(bookingServiceClient, never()).releaseBooking(any());
    }

    @Test
    void nonPendingPayments_areNeverTouchedByTheExpirySweep_evenIfOld() {
        Payment created = paymentRepository.saveAndFlush(payment(PaymentStatus.CREATED));
        Payment success = paymentRepository.saveAndFlush(payment(PaymentStatus.SUCCESS));
        Payment cancelled = paymentRepository.saveAndFlush(payment(PaymentStatus.CANCELLED));
        Instant longAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        backdate(created.getId(), longAgo);
        backdate(success.getId(), longAgo);
        backdate(cancelled.getId(), longAgo);

        int expiredCount = paymentService.expirePendingPayments();

        assertThat(expiredCount).isZero();
        assertThat(paymentRepository.findById(created.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(paymentRepository.findById(success.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(paymentRepository.findById(cancelled.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(bookingServiceClient, never()).releaseBooking(any());
    }

    @Test
    void alreadyExpiredPayment_isNotReprocessed_onASecondSweep() {
        Payment saved = paymentRepository.saveAndFlush(payment(PaymentStatus.PENDING));
        backdate(saved.getId(), Instant.now().minus(30, ChronoUnit.MINUTES));
        when(bookingServiceClient.releaseBooking(any())).thenReturn(true);

        int firstSweep = paymentService.expirePendingPayments();
        int secondSweep = paymentService.expirePendingPayments();

        assertThat(firstSweep).isEqualTo(1);
        assertThat(secondSweep).isZero();
        verify(bookingServiceClient, times(1)).releaseBooking(saved.getBookingId());
    }

    @Test
    void expiringASweep_isIdempotent_repeatedCallsNeverDoubleExpireOrDoubleRelease() {
        // Same claim as alreadyExpiredPayment_..., stated from the
        // "safe to call repeatedly" angle the task asked to prove
        // explicitly: three sweeps back-to-back over the same candidate
        // still only ever expire/release it once.
        Payment saved = paymentRepository.saveAndFlush(payment(PaymentStatus.PENDING));
        backdate(saved.getId(), Instant.now().minus(45, ChronoUnit.MINUTES));
        when(bookingServiceClient.releaseBooking(any())).thenReturn(true);

        int total = paymentService.expirePendingPayments()
                + paymentService.expirePendingPayments()
                + paymentService.expirePendingPayments();

        assertThat(total).isEqualTo(1);
        verify(bookingServiceClient, times(1)).releaseBooking(any());
    }

    @Test
    void expiredPayment_releaseBookingCall_usesThePaymentsOwnBooking() {
        Payment saved = paymentRepository.saveAndFlush(payment(PaymentStatus.PENDING));
        backdate(saved.getId(), Instant.now().minus(20, ChronoUnit.MINUTES));
        when(bookingServiceClient.releaseBooking(any())).thenReturn(true);

        paymentService.expirePendingPayments();

        verify(bookingServiceClient).releaseBooking(saved.getBookingId());
    }

    @Test
    void expiredPayment_releaseBookingFails_bookingSyncStaysPending_forReconciliationToRetry() {
        Payment saved = paymentRepository.saveAndFlush(payment(PaymentStatus.PENDING));
        backdate(saved.getId(), Instant.now().minus(20, ChronoUnit.MINUTES));
        when(bookingServiceClient.releaseBooking(any())).thenReturn(false);

        int expiredCount = paymentService.expirePendingPayments();

        assertThat(expiredCount).isEqualTo(1);
        Payment reloaded = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(reloaded.getBookingSyncStatus()).isEqualTo(BookingSyncStatus.PENDING);
    }
}
