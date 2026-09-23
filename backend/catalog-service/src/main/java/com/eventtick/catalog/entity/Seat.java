package com.eventtick.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Maps to the {@code seats} table (see
 * {@code database/migrations/0006_create_seats_table.up.sql}) — the
 * permanent physical seating layout of a venue (e.g. Section A, Row 3,
 * Seat 12).
 *
 * <p>{@link #venue} is a real {@code @ManyToOne} (unlike a cross-service
 * reference) because {@link Venue} is owned by this same service.
 * {@code fetch = FetchType.LAZY} to avoid unintended eager loading.
 *
 * <p><b>Deliberately has no availability/booked/held state and no direct
 * link to {@code Show}.</b> Whether a seat is available for a particular
 * show is booking-service's {@code show_seats} table, not a property of
 * the physical seat. No {@code show_seats} entity exists in this service
 * — see {@code docs/architecture.md} §17 (service ownership boundary).
 *
 * <p>No {@code created_at}/{@code updated_at} fields: the {@code seats}
 * table has neither column and no update trigger — physical seat rows
 * are static reference data, matching the migration exactly.
 */
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK to venues.id (fk_seats_venue). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "section", nullable = false, length = 50)
    private String section;

    /**
     * Row label within the section (e.g. "A", "12"). Mapped with a
     * backtick-quoted column name so Hibernate always emits it quoted in
     * generated SQL — {@code ROW} is a reserved SQL keyword, exactly why
     * the migration itself declares this column as {@code "row"}.
     */
    @Column(name = "`row`", nullable = false, length = 10)
    private String row;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    /** chk_seats_seat_type: STANDARD, PREMIUM, VIP. */
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

    public Seat() {
        // Required by JPA. Public (not protected) so a future
        // com.eventtick.catalog.service class can construct a new Seat
        // directly.
    }

    public UUID getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getRow() {
        return row;
    }

    public void setRow(String row) {
        this.row = row;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(Integer seatNumber) {
        this.seatNumber = seatNumber;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public void setSeatType(SeatType seatType) {
        this.seatType = seatType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Seat other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
