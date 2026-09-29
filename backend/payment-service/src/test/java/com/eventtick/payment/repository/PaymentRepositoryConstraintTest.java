package com.eventtick.payment.repository;

import com.eventtick.payment.entity.Payment;
import com.eventtick.payment.entity.PaymentStatus;
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
 * Proves the {@code idempotency_key} uniqueness constraint is actually
 * enforced at the database level (not just an application-side check) —
 * against a real H2 schema generated from the entity mapping
 * ({@code ddl-auto: create-drop}, see {@code src/test/resources/application.yml}),
 * mirroring user-service's own precedent for its {@code uq_users_email}
 * constraint.
 *
 * <p><b>Known, documented gap:</b> H2 2.2.224 (this project's test H2
 * version) does not support PostgreSQL's partial/filtered unique indexes
 * (a {@code CREATE UNIQUE INDEX ... WHERE ...} clause is a syntax error in
 * H2), confirmed by direct experimentation during this phase. This means
 * {@code uq_payments_one_active_per_booking}'s real, race-condition-safe
 * database-level enforcement cannot be exercised by an H2-backed test —
 * only the actual PostgreSQL migration
 * (0011_create_payments_table.up.sql) creates it. Hibernate's
 * {@code ddl-auto: create-drop} schema generation from the {@link Payment}
 * entity has no way to express a partial index either (JPA annotations
 * only support whole-column uniqueness). The "one live payment per
 * booking" rule is instead covered at the application layer by
 * {@code PaymentServiceTest}'s duplicate-payment tests, and this class's
 * own {@link #uniqueConstraint_idempotencyKey_isEnforced} test proves the
 * *mechanism* (a real unique index causing a real
 * {@code DataIntegrityViolationException}) works for the constraint H2
 * *can* express.
 */
@SpringBootTest
class PaymentRepositoryConstraintTest {

    @Autowired
    private PaymentRepository paymentRepository;

    private static Payment payment(String idempotencyKey) {
        Payment payment = new Payment();
        payment.setBookingId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setProvider("MOCK");
        payment.setIdempotencyKey(idempotencyKey);
        return payment;
    }

    @Test
    void uniqueConstraint_idempotencyKey_isEnforced() {
        String key = "dup-key-" + UUID.randomUUID();
        paymentRepository.saveAndFlush(payment(key));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment(key)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentIdempotencyKeys_forTheSameBooking_areBothAllowed_atTheRepositoryLevel() {
        // Documents the H2 limitation above: without the partial index,
        // two CREATED payments for the SAME booking with different keys
        // succeed at this layer even though the real Postgres migration
        // would reject the second one. PaymentServiceTest is what proves
        // the application-level guard that stands in for it in tests.
        UUID bookingId = UUID.randomUUID();
        Payment first = payment("k1-" + UUID.randomUUID());
        first.setBookingId(bookingId);
        Payment second = payment("k2-" + UUID.randomUUID());
        second.setBookingId(bookingId);

        paymentRepository.saveAndFlush(first);
        paymentRepository.saveAndFlush(second);

        assertThat(paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)).hasSize(2);
    }

    @Test
    void findLiveByBookingId_onlyMatchesLiveStatuses() {
        UUID bookingId = UUID.randomUUID();
        Payment failed = payment("live-check-failed-" + UUID.randomUUID());
        failed.setBookingId(bookingId);
        failed.setStatus(PaymentStatus.FAILED);
        paymentRepository.saveAndFlush(failed);

        assertThat(paymentRepository.findLiveByBookingId(bookingId, List.of(PaymentStatus.CREATED, PaymentStatus.PENDING, PaymentStatus.SUCCESS)))
                .isEmpty();

        Payment pending = payment("live-check-pending-" + UUID.randomUUID());
        pending.setBookingId(bookingId);
        pending.setStatus(PaymentStatus.PENDING);
        paymentRepository.saveAndFlush(pending);

        assertThat(paymentRepository.findLiveByBookingId(bookingId, List.of(PaymentStatus.CREATED, PaymentStatus.PENDING, PaymentStatus.SUCCESS)))
                .isPresent();
    }
}
