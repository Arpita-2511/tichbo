package com.eventtick.payment.service;

import com.eventtick.payment.client.BookingServiceClient;
import com.eventtick.payment.entity.BookingSyncStatus;
import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;
import com.eventtick.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 15 Step 3: {@link PaymentService#reconcilePendingBookingSync()} —
 * the scenario-G mechanism (a terminal payment whose booking-service side
 * effect was never acknowledged). Same real-H2 + mocked-{@link
 * BookingServiceClient} style as {@link PaymentExpirationTest}; the
 * candidate set is always read fresh from {@code payments.booking_sync_status}
 * (see {@link PaymentRepository#findByStatusInAndBookingSyncStatus}), never
 * an in-memory queue — {@link #reconciliation_survivesARestart_becauseStateIsInTheDatabaseNotMemory}
 * proves this directly by handing a freshly-constructed {@link
 * PaymentService} instance a candidate it never created itself.
 */
@SpringBootTest
class PaymentReconciliationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private BookingServiceClient bookingServiceClient;

    @Value("${payment.default-currency}")
    private String defaultCurrency;

    /**
     * Aggregate-count assertions here (e.g. "exactly 1 reconciled") only
     * hold if the {@code payments} table starts empty for each test — see
     * {@code PaymentExpirationTest#cleanUp}'s identical Javadoc for why
     * this uses per-test cleanup rather than a class/method-level
     * {@code @Transactional}.
     */
    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAll();
    }

    private static Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setBookingId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("750.00"));
        payment.setCurrency("INR");
        payment.setStatus(status);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey("reconcile-test-" + UUID.randomUUID());
        return payment;
    }

    private Payment persistWithPendingSync(PaymentStatus status) {
        Payment saved = paymentRepository.saveAndFlush(payment(status));
        assertThat(saved.getBookingSyncStatus()).isEqualTo(BookingSyncStatus.PENDING);
        return saved;
    }

    @Test
    void successPayment_confirmationSucceeds_isReconciled_andMarkedSynced() {
        Payment saved = persistWithPendingSync(PaymentStatus.SUCCESS);
        when(bookingServiceClient.confirmBooking(saved.getBookingId())).thenReturn(true);

        int reconciledCount = paymentService.reconcilePendingBookingSync();

        assertThat(reconciledCount).isEqualTo(1);
        assertThat(paymentRepository.findById(saved.getId()).orElseThrow().getBookingSyncStatus()).isEqualTo(BookingSyncStatus.DONE);
        verify(bookingServiceClient).confirmBooking(saved.getBookingId());
        verify(bookingServiceClient, never()).releaseBooking(any());
    }

    @Test
    void successPayment_confirmationTemporarilyFails_staysPending_paymentStatusUnchanged() {
        Payment saved = persistWithPendingSync(PaymentStatus.SUCCESS);
        when(bookingServiceClient.confirmBooking(saved.getBookingId())).thenReturn(false);

        int reconciledCount = paymentService.reconcilePendingBookingSync();

        assertThat(reconciledCount).isZero();
        Payment reloaded = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBookingSyncStatus()).isEqualTo(BookingSyncStatus.PENDING);
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.SUCCESS); // never reverted
    }

    @Test
    void failedConfirmation_isRetried_onASubsequentSweep_andSucceedsWithoutADuplicateBooking() {
        Payment saved = persistWithPendingSync(PaymentStatus.SUCCESS);
        when(bookingServiceClient.confirmBooking(saved.getBookingId())).thenReturn(false, true);

        int firstSweep = paymentService.reconcilePendingBookingSync();
        int secondSweep = paymentService.reconcilePendingBookingSync();

        assertThat(firstSweep).isZero();
        assertThat(secondSweep).isEqualTo(1);
        assertThat(paymentRepository.findById(saved.getId()).orElseThrow().getBookingSyncStatus()).isEqualTo(BookingSyncStatus.DONE);
        // Exactly two confirm attempts total (one failed, one succeeded) —
        // never more than one call per sweep for the same still-pending
        // candidate, so no duplicate booking-side confirmation is ever
        // fired for a single reconciliation pass.
        verify(bookingServiceClient, times(2)).confirmBooking(saved.getBookingId());
    }

    @Test
    void successfulRetry_doesNotReconcileAnAlreadySyncedPayment_onAThirdSweep() {
        Payment saved = persistWithPendingSync(PaymentStatus.SUCCESS);
        when(bookingServiceClient.confirmBooking(saved.getBookingId())).thenReturn(true);

        paymentService.reconcilePendingBookingSync();
        int secondSweep = paymentService.reconcilePendingBookingSync();

        assertThat(secondSweep).isZero();
        verify(bookingServiceClient, times(1)).confirmBooking(saved.getBookingId());
    }

    @Test
    void failedPayment_releaseIsRetried_usingReleaseBooking_notConfirmBooking() {
        Payment saved = persistWithPendingSync(PaymentStatus.FAILED);
        when(bookingServiceClient.releaseBooking(saved.getBookingId())).thenReturn(true);

        int reconciledCount = paymentService.reconcilePendingBookingSync();

        assertThat(reconciledCount).isEqualTo(1);
        assertThat(paymentRepository.findById(saved.getId()).orElseThrow().getBookingSyncStatus()).isEqualTo(BookingSyncStatus.DONE);
        verify(bookingServiceClient).releaseBooking(saved.getBookingId());
        verify(bookingServiceClient, never()).confirmBooking(any());
    }

    @Test
    void expiredPayment_releaseIsRetried_sameAsFailed() {
        Payment saved = persistWithPendingSync(PaymentStatus.EXPIRED);
        when(bookingServiceClient.releaseBooking(saved.getBookingId())).thenReturn(true);

        int reconciledCount = paymentService.reconcilePendingBookingSync();

        assertThat(reconciledCount).isEqualTo(1);
        assertThat(paymentRepository.findById(saved.getId()).orElseThrow().getBookingSyncStatus()).isEqualTo(BookingSyncStatus.DONE);
    }

    @Test
    void createdAndPendingPayments_areNeverCandidatesForReconciliation_regardlessOfSyncStatus() {
        Payment created = persistWithPendingSync(PaymentStatus.CREATED);
        Payment pending = persistWithPendingSync(PaymentStatus.PENDING);

        int reconciledCount = paymentService.reconcilePendingBookingSync();

        assertThat(reconciledCount).isZero();
        verify(bookingServiceClient, never()).confirmBooking(any());
        verify(bookingServiceClient, never()).releaseBooking(any());
    }

    @Test
    void cancelledPayment_isNeverAReconciliationCandidate_ithasNoBookingSideEffectToRetry() {
        // CANCELLED has no booking-service side effect of its own to
        // reconcile (nothing was ever charged), so it's deliberately absent
        // from PaymentService.RECONCILIATION_STATUSES.
        Payment cancelled = persistWithPendingSync(PaymentStatus.CANCELLED);

        int reconciledCount = paymentService.reconcilePendingBookingSync();

        assertThat(reconciledCount).isZero();
        verify(bookingServiceClient, never()).confirmBooking(any());
        verify(bookingServiceClient, never()).releaseBooking(any());
    }

    @Test
    void reconciliation_survivesARestart_becauseStateIsInTheDatabaseNotMemory() {
        // Simulates "the process restarted between the payment being
        // recorded and it being reconciled": a brand-new PaymentService
        // instance (a fresh in-memory object graph, sharing only the
        // database) still finds and reconciles a candidate it had no part
        // in creating — proving the candidate set comes entirely from
        // payments.booking_sync_status, not any in-process queue.
        Payment saved = persistWithPendingSync(PaymentStatus.SUCCESS);
        when(bookingServiceClient.confirmBooking(saved.getBookingId())).thenReturn(true);

        PaymentService freshInstance = new PaymentService(paymentRepository, bookingServiceClient, null, null, defaultCurrency, 15L);
        int reconciledCount = freshInstance.reconcilePendingBookingSync();

        assertThat(reconciledCount).isEqualTo(1);
        assertThat(paymentRepository.findById(saved.getId()).orElseThrow().getBookingSyncStatus()).isEqualTo(BookingSyncStatus.DONE);
    }
}
