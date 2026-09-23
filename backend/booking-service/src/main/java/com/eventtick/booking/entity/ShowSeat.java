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
 * Maps to the {@code show_seats} table (see
 * {@code database/migrations/0008_create_show_seats_table.up.sql}) — the
 * availability of one physical seat for one specific show: {@code showId},
 * {@code seatId}, {@code status}, and {@code price}, exactly as the
 * migration defines them.
 *
 * <p><b>Cross-service foreign keys ({@code showId}, {@code seatId}):</b>
 * {@code shows} and {@code seats} are owned by catalog-service, not
 * booking-service (see {@code docs/architecture.md} §17: "a service
 * should not directly query another service's database"). Even though
 * both tables currently live in the same physical {@code eventtick_db},
 * this entity does <b>not</b> declare a JPA {@code @ManyToOne} association
 * to a {@code Show} or {@code Seat} entity, and booking-service does not
 * define its own duplicate {@code Show}/{@code Seat} entity classes. The
 * columns are mapped as plain {@link UUID} fields — foreign key
 * <i>references</i> with no JPA-managed relationship. If booking-service
 * ever needs details about a show or seat (e.g. its start time or seat
 * label), that should be a call to catalog-service's API, not a JPA join
 * across the ownership boundary. This keeps the mapping correct even
 * after the databases are physically split apart, per the architecture's
 * stated future target.
 *
 * <p>{@code createdAt}/{@code updatedAt} are marked
 * {@code insertable = false, updatable = false}: the database, not this
 * entity, is the source of truth for these values — {@code created_at}
 * via its column {@code DEFAULT now()} and {@code updated_at} via the
 * {@code trg_show_seats_set_updated_at} trigger. Hibernate never writes to
 * these columns.
 */
@Entity
@Table(name = "show_seats")
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK to {@code shows.id}, owned by catalog-service. See class Javadoc. */
    @Column(name = "show_id", nullable = false)
    private UUID showId;

    /** FK to {@code seats.id}, owned by catalog-service. See class Javadoc. */
    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    /** chk_show_seats_status: AVAILABLE, HELD, BOOKED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShowSeatStatus status;

    /** Ticket price for this seat at this show. */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected ShowSeat() {
        // Required by JPA.
    }

    public UUID getId() {
        return id;
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public void setSeatId(UUID seatId) {
        this.seatId = seatId;
    }

    public ShowSeatStatus getStatus() {
        return status;
    }

    public void setStatus(ShowSeatStatus status) {
        this.status = status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
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
        if (!(o instanceof ShowSeat other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
