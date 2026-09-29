package com.eventtick.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps to {@code payment_event_audit} (see
 * {@code database/migrations/0016_create_payment_event_audit_table.up.sql}).
 * One row per successfully consumed {@code PaymentSucceeded} event — the
 * same shape of projection {@link BookingEventAudit} already is for
 * {@code BookingCreated}, just for the payment domain (Phase 16 Step 4).
 * Not a second source of truth for payments: {@code paymentId}/{@code
 * bookingId}/{@code userId} are plain copied references, not JPA
 * relationships to any entity this service doesn't itself own.
 *
 * <p>Implements {@link Persistable} for the same reason {@link
 * BookingEventAudit} does: {@code eventId} has no {@code @GeneratedValue}
 * (it is the exact id from the consumed envelope), so without this,
 * Spring Data's default "is this new?" check would always say "no,"
 * causing {@code save(...)} to {@code merge} instead of {@code persist} —
 * silently upserting on a duplicate {@code eventId} instead of failing the
 * unique-constraint check idempotent consumption depends on.
 */
@Entity
@Table(name = "payment_event_audit")
public class PaymentEventAudit implements Persistable<UUID> {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", nullable = false, length = 255)
    private String correlationId;

    // Schema-generation hint only (matches BookingEventAudit.processedAt's
    // own precedent) — inert against the real Postgres migration, which
    // already has its own DEFAULT now().
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
    private Instant processedAt;

    @jakarta.persistence.Transient
    private boolean isNew = false;

    public PaymentEventAudit() {
        // Required by JPA. isNew stays false: this constructor is what
        // Hibernate uses to reconstruct a row already in the database.
    }

    public PaymentEventAudit(UUID eventId, String eventType, UUID paymentId, UUID bookingId, UUID userId,
                              BigDecimal amount, String currency, String providerReference,
                              Instant occurredAt, String correlationId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.providerReference = providerReference;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PaymentEventAudit other)) {
            return false;
        }
        return eventId != null && eventId.equals(other.eventId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
