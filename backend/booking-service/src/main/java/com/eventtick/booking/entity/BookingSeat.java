package com.eventtick.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code booking_seats} table (see
 * {@code database/migrations/0010_create_booking_seats_table.up.sql}) — a
 * single seat line item within a {@link Booking}, referencing the
 * {@link ShowSeat} (physical seat + show pairing) it reserves.
 *
 * <p>Both {@link #booking} and {@link #showSeat} are real
 * {@code @ManyToOne} associations (unlike {@code Booking.userId}/{@code
 * showId}), because {@link Booking} and {@link ShowSeat} are both owned
 * by booking-service itself — no cross-service boundary is crossed here.
 * Both are {@code fetch = FetchType.LAZY} to avoid unintended eager
 * loading.
 *
 * <p>There is deliberately no {@code updated_at} field: the
 * {@code booking_seats} table has no such column and no update trigger —
 * a line item is written once and not mutated in place (see the table
 * comment in the migration). {@code createdAt} is {@code insertable =
 * false, updatable = false}: the database's column default owns this
 * value, not this entity.
 */
@Entity
@Table(name = "booking_seats")
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK to {@code bookings.id} (fk_booking_seats_booking). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** FK to {@code show_seats.id} (fk_booking_seats_show_seat). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_seat_id", nullable = false)
    private ShowSeat showSeat;

    /** Snapshot of ShowSeat.price at the moment this seat was added to the booking. */
    @Column(name = "price_at_booking", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtBooking;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public BookingSeat() {
        // Required by JPA. Public (not protected) so application code in
        // other packages (e.g. com.eventtick.booking.service) can
        // construct a new BookingSeat directly.
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public ShowSeat getShowSeat() {
        return showSeat;
    }

    public void setShowSeat(ShowSeat showSeat) {
        this.showSeat = showSeat;
    }

    public BigDecimal getPriceAtBooking() {
        return priceAtBooking;
    }

    public void setPriceAtBooking(BigDecimal priceAtBooking) {
        this.priceAtBooking = priceAtBooking;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookingSeat other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
