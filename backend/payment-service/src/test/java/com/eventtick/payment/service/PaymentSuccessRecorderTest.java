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

import java.math.BigDecimal;
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
}
