package com.eventtick.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code bookings} table (see
 * {@code database/migrations/0009_create_bookings_table.up.sql}) — a
 * user's booking for exactly one show.
 *
 * <p><b>Cross-service foreign keys ({@code userId}, {@code showId}):</b>
 * {@code users} is owned by user-service and {@code shows} by
 * catalog-service — not by booking-service. As with {@link ShowSeat}'s
 * {@code showId}/{@code seatId}, these are mapped as plain {@link UUID}
 * fields rather than JPA {@code @ManyToOne} associations, and no
 * duplicate {@code User}/{@code Show} entity classes are defined in this
 * service. See {@link ShowSeat}'s class Javadoc for the full reasoning.
 *
 * <p>This entity intentionally has no {@code @OneToMany} collection of its
 * {@link BookingSeat} line items — that association, along with its
 * fetch/cascade behavior, is a decision for when booking creation/read
 * logic is actually implemented, not for the entity-mapping pass.
 *
 * <p>{@code createdAt}/{@code updatedAt} are {@code insertable = false,
 * updatable = false}: the database owns these values (column default and
 * the {@code trg_bookings_set_updated_at} trigger), not this entity.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK to {@code users.id}, owned by user-service. See class Javadoc. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** FK to {@code shows.id}, owned by catalog-service. See class Javadoc. */
    @Column(name = "show_id", nullable = false)
    private UUID showId;

    /** chk_bookings_status: PENDING, CONFIRMED, CANCELLED, FAILED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    /** Total charged for this booking; expected to equal the sum of its BookingSeat line items. */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Booking() {
        // Required by JPA. Public (not protected) so application code in
        // other packages (e.g. com.eventtick.booking.service) can
        // construct a new Booking directly.
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
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
        if (!(o instanceof Booking other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
