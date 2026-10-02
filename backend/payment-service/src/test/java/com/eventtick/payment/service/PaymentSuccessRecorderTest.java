package com.eventtick.payment.service;

import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;
import com.eventtick.payment.outbox.OutboxEventStatus;
import com.eventtick.payment.outbox.PaymentOutboxEvent;
import com.eventtick.payment.outbox.PaymentOutboxEventRepository;
import com.eventtick.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PaymentSuccessRecorder} against a real H2-backed {@link
 * PaymentRepository}/{@link PaymentOutboxEventRepository} (same
 * {@code @SpringBootTest} style as {@code PaymentExpirationTest}) — proves
 * this step's central transaction-boundary requirement (docs/
 * architecture.md §50.4, kickoff Steps 2/4/16): the {@code SUCCESS} status
 * write and the {@code PaymentSucceeded} outbox row commit atomically, not
 * merely "usually together."
 */
@SpringBootTest
class PaymentSuccessRecorderTest {

    @Autowired
    private PaymentSuccessRecorder paymentSuccessRecorder;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOutboxEventRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    /** A payment already transitioned to SUCCESS in memory, not yet saved — exactly what {@code PaymentService.chargeAndResolve} hands to {@link PaymentSuccessRecorder}. */
    private static Payment inMemorySuccessPayment(String providerReference) {
        Payment payment = new Payment();
        payment.setBookingId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey("psr-test-" + UUID.randomUUID());
        payment.setProviderReference(providerReference);
        return payment;
    }

    /** A payment already transitioned to FAILED in memory, not yet saved — exactly what {@code PaymentService.chargeAndResolve} hands to {@link PaymentSuccessRecorder#recordFailure}. */
    private static Payment inMemoryFailedPayment(String providerReference) {
        Payment payment = new Payment();
        payment.setBookingId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.FAILED);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey("psr-fail-test-" + UUID.randomUUID());
        payment.setProviderReference(providerReference);
        return payment;
    }

    /** A real, persisted PENDING payment, backdated so it's an expiry candidate — mirrors {@code PaymentExpirationTest}'s own fixture/backdate technique exactly. */
    private Payment persistedPendingPayment(Instant createdAt) {
        Payment payment = new Payment();
        payment.setBookingId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("500.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey("psr-expiry-test-" + UUID.randomUUID());
        Payment saved = paymentRepository.saveAndFlush(payment);
        jdbcTemplate.update("UPDATE payments SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), saved.getId());
        return saved;
    }

    private PaymentOutboxEvent onlyOutboxRowFor(UUID paymentId) {
        List<PaymentOutboxEvent> rows = outboxRepository.findAll().stream()
                .filter(r -> r.getAggregateId().equals(paymentId))
                .toList();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @Test
    void recordSuccess_createsExactlyOnePendingOutboxRow_typePaymentSucceeded() {
        Payment saved = paymentSuccessRecorder.recordSuccess(inMemorySuccessPayment("ref-1"), "corr-1");

        PaymentOutboxEvent row = onlyOutboxRowFor(saved.getId());
        assertThat(row.getEventType()).isEqualTo("PaymentSucceeded");
        assertThat(row.getAggregateType()).isEqualTo("Payment");
        assertThat(row.getTopic()).isEqualTo("eventtick.payment");
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void recordSuccess_alsoSavesThePaymentItself_asSuccess() {
        Payment saved = paymentSuccessRecorder.recordSuccess(inMemorySuccessPayment("ref-2"), "corr-1");

        Payment reloaded = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(reloaded.getProviderReference()).isEqualTo("ref-2");
    }

    @Test
    void recordSuccess_outboxPayload_containsTheExpectedFields() throws Exception {
        Payment input = inMemorySuccessPayment("ref-3");
        Payment saved = paymentSuccessRecorder.recordSuccess(input, "corr-payload");

        PaymentOutboxEvent row = onlyOutboxRowFor(saved.getId());
        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("PaymentSucceeded");
        assertThat(envelope.get("producer").asText()).isEqualTo("payment-service");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(saved.getId().toString());
        assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-payload");

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("paymentId").asText()).isEqualTo(saved.getId().toString());
        assertThat(payload.get("bookingId").asText()).isEqualTo(saved.getBookingId().toString());
        assertThat(payload.get("userId").asText()).isEqualTo(saved.getUserId().toString());
        assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(payload.get("currency").asText()).isEqualTo("INR");
        assertThat(payload.get("providerReference").asText()).isEqualTo("ref-3");
    }

    @Test
    void recordSuccess_blankCorrelationId_getsAFreshGeneratedOne() throws Exception {
        Payment saved = paymentSuccessRecorder.recordSuccess(inMemorySuccessPayment("ref-4"), "");

        PaymentOutboxEvent row = onlyOutboxRowFor(saved.getId());
        JsonNode envelope = objectMapper.readTree(row.getPayload());
        String correlationId = envelope.get("correlationId").asText();
        assertThat(correlationId).isNotBlank();
        // A valid UUID string — PaymentOutboxService's own documented
        // fallback for a blank correlationId (mirrors booking-service's
        // OutboxService, see that class's own test coverage).
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void recordSuccess_twoDifferentPayments_getTwoDifferentEventIds_bothUnique() {
        Payment first = paymentSuccessRecorder.recordSuccess(inMemorySuccessPayment("ref-5a"), "corr-a");
        Payment second = paymentSuccessRecorder.recordSuccess(inMemorySuccessPayment("ref-5b"), "corr-b");

        UUID firstEventId = onlyOutboxRowFor(first.getId()).getEventId();
        UUID secondEventId = onlyOutboxRowFor(second.getId()).getEventId();
        assertThat(firstEventId).isNotEqualTo(secondEventId);
        assertThat(outboxRepository.findAll()).hasSize(2);
    }

    @Test
    void recordSuccess_ifThePaymentWriteViolatesAConstraint_rollsBackTheOutboxRowToo() {
        // currency is NOT NULL (see the payments table / Payment entity) —
        // forcing that violation inside the SAME @Transactional method
        // that also writes the outbox row proves the two are not two
        // independent writes that merely usually succeed together: a
        // failure in one half genuinely rolls back the other.
        Payment broken = inMemorySuccessPayment("ref-6");
        broken.setCurrency(null);

        assertThatThrownBy(() -> paymentSuccessRecorder.recordSuccess(broken, "corr-6"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    // ───────────────────────────────────────────────────────────────────
    // Phase 16 Step 5: recordFailure
    // ───────────────────────────────────────────────────────────────────

    @Test
    void recordFailure_createsExactlyOnePendingOutboxRow_typePaymentFailed() {
        Payment saved = paymentSuccessRecorder.recordFailure(inMemoryFailedPayment("ref-f1"), "corr-f1", "card declined");

        PaymentOutboxEvent row = onlyOutboxRowFor(saved.getId());
        assertThat(row.getEventType()).isEqualTo("PaymentFailed");
        assertThat(row.getAggregateType()).isEqualTo("Payment");
        assertThat(row.getTopic()).isEqualTo("eventtick.payment");
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void recordFailure_alsoSavesThePaymentItself_asFailed() {
        Payment saved = paymentSuccessRecorder.recordFailure(inMemoryFailedPayment("ref-f2"), "corr-f1", "card declined");

        Payment reloaded = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reloaded.getProviderReference()).isEqualTo("ref-f2");
    }

    @Test
    void recordFailure_outboxPayload_containsTheExpectedFields_includingFailureReason() throws Exception {
        Payment input = inMemoryFailedPayment("ref-f3");
        Payment saved = paymentSuccessRecorder.recordFailure(input, "corr-f-payload", "insufficient funds");

        PaymentOutboxEvent row = onlyOutboxRowFor(saved.getId());
        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("PaymentFailed");
        assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-f-payload");

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("paymentId").asText()).isEqualTo(saved.getId().toString());
        assertThat(payload.get("bookingId").asText()).isEqualTo(saved.getBookingId().toString());
        assertThat(payload.get("userId").asText()).isEqualTo(saved.getUserId().toString());
        assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(payload.get("currency").asText()).isEqualTo("INR");
        assertThat(payload.get("providerReference").asText()).isEqualTo("ref-f3");
        assertThat(payload.get("failureReason").asText()).isEqualTo("insufficient funds");
    }

    @Test
    void recordFailure_nullProviderReference_isRepresentedAsJsonNull_notARequiredField() throws Exception {
        // A failure that never reached the provider (e.g. a validation
        // rejection) — PaymentProviderResult's own documented case.
        Payment saved = paymentSuccessRecorder.recordFailure(inMemoryFailedPayment(null), "corr-f-null", "validation rejected");

        PaymentOutboxEvent row = onlyOutboxRowFor(saved.getId());
        JsonNode payload = objectMapper.readTree(row.getPayload()).get("payload");
        assertThat(payload.get("providerReference").isNull()).isTrue();
    }

    @Test
    void recordFailure_ifThePaymentWriteViolatesAConstraint_rollsBackTheOutboxRowToo() {
        Payment broken = inMemoryFailedPayment("ref-f4");
        broken.setCurrency(null);

        assertThatThrownBy(() -> paymentSuccessRecorder.recordFailure(broken, "corr-f4", "card declined"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    // ───────────────────────────────────────────────────────────────────
    // Phase 16 Step 5: recordExpiryIfStillPending
    // ───────────────────────────────────────────────────────────────────

    @Test
    void recordExpiryIfStillPending_pendingPastCutoff_expiresAndCreatesExactlyOnePendingOutboxRow() {
        Payment pending = persistedPendingPayment(Instant.now().minus(20, ChronoUnit.MINUTES));

        boolean expired = paymentSuccessRecorder.recordExpiryIfStillPending(pending, "corr-e1");

        assertThat(expired).isTrue();
        Payment reloaded = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        PaymentOutboxEvent row = onlyOutboxRowFor(pending.getId());
        assertThat(row.getEventType()).isEqualTo("PaymentExpired");
        assertThat(row.getTopic()).isEqualTo("eventtick.payment");
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void recordExpiryIfStillPending_outboxPayload_containsTheExpectedFields_noProviderReferenceField() throws Exception {
        Payment pending = persistedPendingPayment(Instant.now().minus(20, ChronoUnit.MINUTES));

        paymentSuccessRecorder.recordExpiryIfStillPending(pending, "corr-e-payload");

        PaymentOutboxEvent row = onlyOutboxRowFor(pending.getId());
        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("PaymentExpired");
        assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-e-payload");

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("paymentId").asText()).isEqualTo(pending.getId().toString());
        assertThat(payload.get("bookingId").asText()).isEqualTo(pending.getBookingId().toString());
        assertThat(payload.get("userId").asText()).isEqualTo(pending.getUserId().toString());
        assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(payload.get("currency").asText()).isEqualTo("INR");
        assertThat(payload.has("providerReference")).isFalse();
        assertThat(payload.has("failureReason")).isFalse();
    }

    @Test
    void recordExpiryIfStillPending_blankCorrelationId_getsAFreshGeneratedOne() throws Exception {
        // The scheduled sweep's own background-trigger case (no originating
        // request) — PaymentOutboxService's own documented fallback.
        Payment pending = persistedPendingPayment(Instant.now().minus(20, ChronoUnit.MINUTES));

        paymentSuccessRecorder.recordExpiryIfStillPending(pending, null);

        PaymentOutboxEvent row = onlyOutboxRowFor(pending.getId());
        String correlationId = objectMapper.readTree(row.getPayload()).get("correlationId").asText();
        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void recordExpiryIfStillPending_alreadyResolved_returnsFalse_createsNoOutboxRow() {
        // Mirrors PaymentExpirationTest's "already resolved between read and
        // conditional update" scenario, at this recorder's own level: a
        // candidate that is no longer PENDING (e.g. the normal flow already
        // moved it to SUCCESS/FAILED) must not be expired or published.
        Payment pending = persistedPendingPayment(Instant.now().minus(20, ChronoUnit.MINUTES));
        // Simulates the normal creation flow resolving this same payment to
        // SUCCESS between the sweep's own candidate read and this call —
        // the exact race expireIfStillPending's WHERE guard defends against.
        pending.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(pending);

        boolean expired = paymentSuccessRecorder.recordExpiryIfStillPending(pending, "corr-e-race");

        assertThat(expired).isFalse();
        assertThat(outboxRepository.findAll()).isEmpty();
        Payment reloaded = paymentRepository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void recordExpiryIfStillPending_calledTwiceForTheSameCandidate_onlyExpiresAndPublishesOnce() {
        // The same "safe to call repeatedly" guarantee PaymentExpirationTest
        // already proves for the sweep as a whole, demonstrated directly at
        // this recorder's own level.
        Payment pending = persistedPendingPayment(Instant.now().minus(20, ChronoUnit.MINUTES));

        boolean first = paymentSuccessRecorder.recordExpiryIfStillPending(pending, "corr-e-first");
        boolean second = paymentSuccessRecorder.recordExpiryIfStillPending(pending, "corr-e-second");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(outboxRepository.findAll()).hasSize(1);
    }
}
