package com.eventtick.payment.service;

import com.eventtick.payment.client.BookingServiceClient;
import com.eventtick.payment.client.BookingSummary;
import com.eventtick.payment.entity.PaymentStatus;
import com.eventtick.payment.outbox.OutboxEventStatus;
import com.eventtick.payment.outbox.PaymentOutboxEvent;
import com.eventtick.payment.outbox.PaymentOutboxEventRepository;
import com.eventtick.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Two of Phase 16 Step 4's automated-test requirements that need the
 * <b>real</b> Spring context (real {@link PaymentService}, real {@link
 * PaymentSuccessRecorder}, real H2-backed repositories, a real — but
 * genuinely unreachable, per {@code src/test/resources/application.yml}'s
 * {@code spring.kafka.bootstrap-servers: localhost:1} — Kafka producer
 * bean) rather than a Mockito unit test, since both are claims about how
 * real Spring-managed beans behave together, not about {@link
 * PaymentService}'s own branching logic in isolation (that's {@code
 * PaymentServiceTest}'s job):
 *
 * <ol>
 *   <li>Kafka being unreachable never prevents a payment from reaching
 *   {@code SUCCESS} — the outbox write is a database-only operation (see
 *   {@code PaymentOutboxService#record}); nothing here ever calls
 *   {@code KafkaTemplate#send}.</li>
 *   <li>The service logic never enqueues a second, semantically-duplicate
 *   {@code PaymentSucceeded} event for the same payment merely because
 *   that payment is observed/retried again (a replayed {@code
 *   createPayment} call, or a later reconciliation sweep) — the event
 *   represents the one-time {@code NOT SUCCESS -> SUCCESS} transition,
 *   which {@link com.eventtick.payment.entity.PaymentStatus}'s own state
 *   machine structurally allows only once per payment.</li>
 * </ol>
 *
 * Same real-H2 + mocked-{@link BookingServiceClient} style as {@code
 * PaymentExpirationTest}/{@code PaymentReconciliationTest}.
 */
@SpringBootTest
class PaymentOutboxIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOutboxEventRepository outboxRepository;

    @MockBean
    private BookingServiceClient bookingServiceClient;

    /** See {@code PaymentExpirationTest#cleanUp}'s identical Javadoc. */
    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private List<PaymentOutboxEvent> outboxRowsFor(UUID paymentId) {
        return outboxRepository.findAll().stream().filter(r -> r.getAggregateId().equals(paymentId)).toList();
    }

    @Test
    void createPayment_reachesSuccess_evenThoughKafkaIsUnreachable_outboxRowStaysPending() {
        UUID bookingId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(bookingServiceClient.getBooking(bookingId))
                .thenReturn(new BookingSummary(bookingId, callerId, "PENDING", new BigDecimal("500.00")));
        when(bookingServiceClient.confirmBooking(bookingId)).thenReturn(true);

        PaymentService.PaymentCreationResult result = paymentService.createPayment(
                callerId, bookingId, "kafka-down-" + UUID.randomUUID(), "corr-kafka-down");

        // The whole point: SUCCESS was reached synchronously, with no
        // Kafka broker reachable at all (spring.kafka.bootstrap-servers
        // in this test's own application.yml points nowhere real).
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        List<PaymentOutboxEvent> rows = outboxRowsFor(result.payment().getId());
        assertThat(rows).hasSize(1);
        // Never published — no broker was ever reachable for
        // PaymentOutboxPublisher's own (unscheduled in this test context,
        // per the huge sweep interval) sweep to succeed against.
        assertThat(rows.get(0).getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void createPayment_replayedWithTheSameIdempotencyKey_doesNotEnqueueASecondEvent() {
        UUID bookingId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        String idempotencyKey = "replay-" + UUID.randomUUID();
        when(bookingServiceClient.getBooking(bookingId))
                .thenReturn(new BookingSummary(bookingId, callerId, "PENDING", new BigDecimal("500.00")));
        when(bookingServiceClient.confirmBooking(bookingId)).thenReturn(true);

        PaymentService.PaymentCreationResult first =
                paymentService.createPayment(callerId, bookingId, idempotencyKey, "corr-1");
        assertThat(first.created()).isTrue();
        assertThat(outboxRowsFor(first.payment().getId())).hasSize(1);

        // Same key, same booking — a genuine replay (FR-46), not a second
        // charge or a second event: getBooking is not even called again
        // (the idempotency-key lookup short-circuits first).
        PaymentService.PaymentCreationResult second =
                paymentService.createPayment(callerId, bookingId, idempotencyKey, "corr-2");
        assertThat(second.created()).isFalse();
        assertThat(second.payment().getId()).isEqualTo(first.payment().getId());

        assertThat(outboxRowsFor(first.payment().getId())).hasSize(1);
        assertThat(outboxRepository.findAll()).hasSize(1);
    }

    // ---- Phase 16 Step 5: FAILED path also creates an outbox event ----

    @Test
    void createPayment_providerFails_reachesFailedAndCreatesPaymentFailedOutboxRow() {
        UUID bookingId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(bookingServiceClient.getBooking(bookingId))
                .thenReturn(new BookingSummary(bookingId, callerId, "PENDING", new BigDecimal("500.00")));

        PaymentService.PaymentCreationResult result = paymentService.createPayment(
                callerId, bookingId, "FORCE_FAIL_outbox-" + UUID.randomUUID(), "corr-fail-outbox");

        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.FAILED);

        List<PaymentOutboxEvent> rows = outboxRowsFor(result.payment().getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEventType()).isEqualTo("PaymentFailed");
        assertThat(rows.get(0).getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void reconciliation_afterASuccessfulPayment_doesNotEnqueueASecondEvent() {
        UUID bookingId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(bookingServiceClient.getBooking(bookingId))
                .thenReturn(new BookingSummary(bookingId, callerId, "PENDING", new BigDecimal("500.00")));
        // confirmBooking fails the first time (forcing bookingSyncStatus
        // to stay PENDING, a reconciliation candidate) ...
        when(bookingServiceClient.confirmBooking(bookingId)).thenReturn(false);

        PaymentService.PaymentCreationResult result = paymentService.createPayment(
                callerId, bookingId, "reconcile-" + UUID.randomUUID(), "corr-reconcile");
        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(outboxRowsFor(result.payment().getId())).hasSize(1);

        // ... then succeeds on reconciliation. reconcilePendingBookingSync
        // only retries the booking-service HTTP call — it must never
        // touch the outbox again, since the payment's own SUCCESS
        // transition already happened and was already published exactly
        // once (PaymentStatus.SUCCESS has no outgoing transitions at all —
        // see that enum's own state table).
        when(bookingServiceClient.confirmBooking(bookingId)).thenReturn(true);
        int reconciledCount = paymentService.reconcilePendingBookingSync();

        assertThat(reconciledCount).isEqualTo(1);
        assertThat(outboxRowsFor(result.payment().getId())).hasSize(1);
        assertThat(outboxRepository.findAll()).hasSize(1);
    }
}
