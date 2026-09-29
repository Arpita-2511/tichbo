package com.eventtick.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code payments} table (see
 * {@code database/migrations/0011_create_payments_table.up.sql}) — one
 * payment attempt for exactly one {@code Booking}.
 *
 * <p><b>Cross-service foreign keys ({@code bookingId}, {@code userId}):</b>
 * {@code bookings} is owned by booking-service and {@code users} by
 * user-service — not by payment-service. Exactly like
 * {@code Booking.userId}/{@code Booking.showId} in booking-service, these
 * are mapped as plain {@link UUID} fields rather than JPA
 * {@code @ManyToOne} associations, and no duplicate {@code Booking}/
 * {@code User} entity classes are defined in this service — see
 * {@code docs/architecture.md} §25.1 for the full reasoning. The database
 * still enforces real foreign keys (this project currently shares one
 * physical {@code eventtick_db}), just not at the JPA mapping level.
 *
 * <p>{@code createdAt}/{@code updatedAt} are {@code insertable = false,
 * updatable = false}: the database owns these values (column default and
 * the {@code trg_payments_set_updated_at} trigger), not this entity —
 * same pattern as {@code Booking}.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK to {@code bookings.id}, owned by booking-service. See class Javadoc. */
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    /** FK to {@code users.id}, owned by user-service. See class Javadoc. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Snapshot of the booking's total_amount at payment-creation time — never client-supplied. */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** ISO 4217 currency code, e.g. "INR". See docs/architecture.md §25.1 — no existing convention to reuse. */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** chk_payments_status: CREATED, PENDING, SUCCESS, FAILED, EXPIRED, CANCELLED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /** Which {@link com.eventtick.payment.provider.PaymentProvider} handled this payment, e.g. "MOCK". */
    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    /** Provider-assigned reference; null until the provider call resolves. */
    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    /** Client-supplied idempotency key; unique across all payments (uq_payments_idempotency_key). */
    @Column(name = "idempotency_key", nullable = false, length = 100, unique = true)
    private String idempotencyKey;

    /**
     * Whether booking-service has acknowledged this payment's terminal
     * outcome (confirm for SUCCESS, cancel for FAILED/EXPIRED) — see
     * {@link BookingSyncStatus}'s own Javadoc and
     * {@code database/migrations/0012_add_booking_sync_status_to_payments.up.sql}
     * (Phase 15 Step 3). Meaningless for CREATED/PENDING/CANCELLED
     * payments; defaults to {@code PENDING} and is only ever read/written
     * for SUCCESS/FAILED/EXPIRED ones.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_sync_status", nullable = false, length = 20)
    private BookingSyncStatus bookingSyncStatus = BookingSyncStatus.PENDING;

    // @ColumnDefault is a schema-generation hint only (matches User.createdAt's
    // own precedent) — inert against the real Postgres migration, which
    // already has its own DEFAULT now(); it's what lets Hibernate's
    // ddl-auto: create-drop test schema (H2) generate a workable default,
    // since insertable = false means this entity never supplies the value.
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Payment() {
        // Required by JPA. Public (not protected) so application code in
        // other packages (e.g. com.eventtick.payment.service) can
        // construct a new Payment directly — same convention as Booking.
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public BookingSyncStatus getBookingSyncStatus() {
        return bookingSyncStatus;
    }

    public void setBookingSyncStatus(BookingSyncStatus bookingSyncStatus) {
        this.bookingSyncStatus = bookingSyncStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Payment other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
