package com.eventtick.payment.service;

import com.eventtick.payment.client.BookingServiceClient;
import com.eventtick.payment.client.BookingSummary;
import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;
import com.eventtick.payment.exception.IdempotencyConflictException;
import com.eventtick.payment.exception.PaymentConflictException;
import com.eventtick.payment.exception.PaymentNotFoundException;
import com.eventtick.payment.exception.PaymentOwnershipException;
import com.eventtick.payment.provider.PaymentChargeRequest;
import com.eventtick.payment.provider.PaymentProvider;
import com.eventtick.payment.provider.PaymentProviderResult;
import com.eventtick.payment.repository.PaymentRepository;
import com.eventtick.payment.security.PaymentRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test of {@link PaymentService}'s business logic
 * (booking lookup, ownership, idempotency, provider dispatch) — not a
 * {@code @WebMvcTest}, which would mock this class instead of proving
 * anything about it. Mirrors catalog-service's own
 * {@code ShowServiceCancelTest} in style.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingServiceClient bookingServiceClient;
    @Mock
    private PaymentProvider paymentProvider;
    @Mock
    private PaymentSuccessRecorder paymentSuccessRecorder;

    private PaymentService paymentService;

    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = "key-1";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, bookingServiceClient, paymentProvider,
                paymentSuccessRecorder, "INR", 15L);
        // Mirrors PaymentSuccessRecorder#recordSuccess's own contract
        // (save-and-return) without a real transaction/outbox write — this
        // is a plain Mockito unit test of PaymentService's own branching,
        // not of PaymentSuccessRecorder itself (see PaymentSuccessRecorderTest).
        lenient().when(paymentSuccessRecorder.recordSuccess(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static BookingSummary pendingBooking() {
        return new BookingSummary(BOOKING_ID, CALLER, "PENDING", new BigDecimal("1450.00"));
    }

    private static Payment persistedPayment(PaymentStatus status) {
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        payment.setBookingId(BOOKING_ID);
        payment.setUserId(CALLER);
        payment.setAmount(new BigDecimal("1450.00"));
        payment.setCurrency("INR");
        payment.setStatus(status);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey(IDEMPOTENCY_KEY);
        return payment;
    }

    // ---- happy path: creation + successful provider result ----

    @Test
    void createPayment_newBooking_success_createsAndConfirms() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.id()).thenReturn("MOCK");
        when(paymentProvider.charge(any())).thenReturn(PaymentProviderResult.success("mock_txn_1"));

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test");

        assertThat(result.created()).isTrue();
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.payment().getAmount()).isEqualByComparingTo("1450.00");
        assertThat(result.payment().getCurrency()).isEqualTo("INR");
        assertThat(result.payment().getProviderReference()).isEqualTo("mock_txn_1");
        verify(bookingServiceClient).confirmBooking(BOOKING_ID);
        verify(bookingServiceClient, never()).releaseBooking(any());
    }

    @Test
    void createPayment_amountIsFromTheBooking_neverClientSupplied() {
        // There is no "amount" parameter on createPayment at all — this test
        // documents that guarantee at the API level: the only source of the
        // charged amount is BookingSummary.totalAmount().
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        when(paymentRepository.saveAndFlush(captor.capture())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.charge(any())).thenReturn(PaymentProviderResult.success("ref"));

        paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test");

        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("1450.00");
    }

    // ---- failed provider result ----

    @Test
    void createPayment_providerFails_marksFailedAndReleasesBooking() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.charge(any())).thenReturn(PaymentProviderResult.failure(null, "card declined"));

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test");

        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(bookingServiceClient).releaseBooking(BOOKING_ID);
        verify(bookingServiceClient, never()).confirmBooking(any());
    }

    // ---- idempotency: replay ----

    @Test
    void createPayment_sameKeySameBooking_replaysExisting_doesNotCallProviderAgain() {
        Payment existing = persistedPayment(PaymentStatus.SUCCESS);
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test");

        assertThat(result.created()).isFalse();
        assertThat(result.payment()).isSameAs(existing);
        verify(paymentProvider, never()).charge(any());
        verify(bookingServiceClient, never()).getBooking(any());
    }

    // ---- idempotency: conflict ----

    @Test
    void createPayment_sameKeyDifferentBooking_isConflict() {
        Payment existing = persistedPayment(PaymentStatus.SUCCESS); // bookingId = BOOKING_ID
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));
        UUID differentBooking = UUID.randomUUID();

        assertThatThrownBy(() -> paymentService.createPayment(CALLER, differentBooking, IDEMPOTENCY_KEY, "corr-test"))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(paymentProvider, never()).charge(any());
    }

    // ---- duplicate live payment for booking ----

    @Test
    void createPayment_bookingAlreadyHasALivePayment_isRejected() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        Payment otherAttempt = persistedPayment(PaymentStatus.PENDING);
        otherAttempt.setIdempotencyKey("a-different-checkout-attempt");
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.of(otherAttempt));

        assertThatThrownBy(() -> paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test"))
                .isInstanceOf(PaymentConflictException.class);
        verify(paymentProvider, never()).charge(any());
    }

    @Test
    void createPayment_liveSameKeyPaymentAppearsAfterKeyLookup_isReplayNotConflict() {
        // Regression (Phase 15 Step 4): concurrent double-submit of one key —
        // the loser's key lookup ran before the winner's insert, but its
        // live-payment check ran after. Must replay, not 409.
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        Payment winner = persistedPayment(PaymentStatus.PENDING);
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.of(winner));

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test");

        assertThat(result.created()).isFalse();
        assertThat(result.payment()).isSameAs(winner);
        verify(paymentProvider, never()).charge(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPayment_bookingHasOnlyAFailedPriorPayment_isAllowed() {
        // A new idempotency key for a booking whose only payment is terminal
        // and non-SUCCESS is a legitimate retry — findLiveByBookingId
        // correctly returns empty for that case (SUCCESS is live, FAILED is
        // not), so this is really testing the query's own contract via the
        // service, not inventing new logic.
        when(paymentRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.charge(any())).thenReturn(PaymentProviderResult.success("ref"));

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, "key-2", "corr-test");

        assertThat(result.created()).isTrue();
    }

    @Test
    void createPayment_bookingHasOnlyAnExpiredPriorPayment_isAllowed() {
        // Step 3: EXPIRED must be treated exactly like FAILED for this
        // rule — both are terminal-and-not-live (PaymentStatus.isLive()),
        // so a retry with a new key is legitimate either way.
        when(paymentRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.charge(any())).thenReturn(PaymentProviderResult.success("ref"));

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, "key-3", "corr-test");

        assertThat(result.created()).isTrue();
    }

    @Test
    void createPayment_bookingHasOnlyACancelledPriorPayment_isAllowed() {
        when(paymentRepository.findByIdempotencyKey("key-4")).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.charge(any())).thenReturn(PaymentProviderResult.success("ref"));

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, "key-4", "corr-test");

        assertThat(result.created()).isTrue();
    }

    // ---- Step 3: booking_sync_status is recorded from the confirm/release outcome ----

    @Test
    void createPayment_success_confirmSucceeds_bookingSyncIsMarkedDone() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.charge(any())).thenReturn(PaymentProviderResult.success("ref"));
        when(bookingServiceClient.confirmBooking(BOOKING_ID)).thenReturn(true);

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test");

        assertThat(result.payment().getBookingSyncStatus()).isEqualTo(com.eventtick.payment.entity.BookingSyncStatus.DONE);
        verify(paymentRepository).markBookingSynced(result.payment().getId());
    }

    @Test
    void createPayment_success_confirmFails_bookingSyncStaysPending_forReconciliationToRetry() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.charge(any())).thenReturn(PaymentProviderResult.success("ref"));
        when(bookingServiceClient.confirmBooking(BOOKING_ID)).thenReturn(false);

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test");

        // Payment itself is still SUCCESS — a failed booking sync never
        // rolls back the payment's own already-committed outcome.
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.payment().getBookingSyncStatus()).isEqualTo(com.eventtick.payment.entity.BookingSyncStatus.PENDING);
        verify(paymentRepository, never()).markBookingSynced(any());
    }

    // ---- ownership ----

    @Test
    void createPayment_callerDoesNotOwnTheBooking_isRejected() {
        UUID someoneElse = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(new BookingSummary(BOOKING_ID, someoneElse, "PENDING", BigDecimal.TEN));

        assertThatThrownBy(() -> paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test"))
                .isInstanceOf(PaymentOwnershipException.class);
        verify(paymentProvider, never()).charge(any());
    }

    // ---- booking not payable ----

    @Test
    void createPayment_bookingNotPending_isRejected() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(new BookingSummary(BOOKING_ID, CALLER, "CANCELLED", BigDecimal.TEN));

        assertThatThrownBy(() -> paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test"))
                .isInstanceOf(PaymentConflictException.class);
        verify(paymentProvider, never()).charge(any());
    }

    // ---- DB-level race: idempotency key collision on insert ----

    @Test
    void createPayment_raceOnInsert_idempotencyKeyWonByAConcurrentRequest_replaysIt() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty()) // first check: nothing yet
                .thenReturn(Optional.of(persistedPayment(PaymentStatus.SUCCESS))); // re-check after the race
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_payments_idempotency_key"));

        PaymentService.PaymentCreationResult result = paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test");

        assertThat(result.created()).isFalse();
        verify(paymentProvider, never()).charge(any());
    }

    @Test
    void createPayment_raceOnInsert_oneLivePaymentIndexWonByAConcurrentRequest_isConflict() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(bookingServiceClient.getBooking(BOOKING_ID)).thenReturn(pendingBooking());
        when(paymentRepository.findLiveByBookingId(eq(BOOKING_ID), anyList())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_payments_one_active_per_booking"));

        assertThatThrownBy(() -> paymentService.createPayment(CALLER, BOOKING_ID, IDEMPOTENCY_KEY, "corr-test"))
                .isInstanceOf(PaymentConflictException.class);
        verify(paymentProvider, never()).charge(any());
    }

    // ---- lookup ----

    @Test
    void getPayment_found_returnsIt() {
        Payment payment = persistedPayment(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThat(paymentService.getPayment(payment.getId())).isSameAs(payment);
    }

    @Test
    void getPayment_notFound_throws() {
        UUID missing = UUID.randomUUID();
        when(paymentRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(missing)).isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void getPaymentForBooking_returnsMostRecent() {
        Payment mostRecent = persistedPayment(PaymentStatus.SUCCESS);
        when(paymentRepository.findByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(List.of(mostRecent));

        assertThat(paymentService.getPaymentForBooking(BOOKING_ID)).isSameAs(mostRecent);
    }

    @Test
    void getPaymentForBooking_none_throws() {
        when(paymentRepository.findByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> paymentService.getPaymentForBooking(BOOKING_ID))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    // ---- ownership check for viewing ----

    @Test
    void canView_ownerCanView() {
        Payment payment = persistedPayment(PaymentStatus.SUCCESS); // userId = CALLER
        assertThat(paymentService.canView(payment, CALLER, PaymentRole.CUSTOMER)).isTrue();
    }

    @Test
    void canView_adminCanViewAnyone() {
        Payment payment = persistedPayment(PaymentStatus.SUCCESS);
        assertThat(paymentService.canView(payment, UUID.randomUUID(), PaymentRole.ADMIN)).isTrue();
    }

    @Test
    void canView_anotherCustomerCannotView() {
        Payment payment = persistedPayment(PaymentStatus.SUCCESS);
        assertThat(paymentService.canView(payment, UUID.randomUUID(), PaymentRole.CUSTOMER)).isFalse();
    }
}
