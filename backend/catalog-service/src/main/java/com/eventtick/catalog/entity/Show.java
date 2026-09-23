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

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code shows} table (see
 * {@code database/migrations/0007_create_shows_table.up.sql}) — a
 * specific scheduled occurrence of {@link Content} at a {@link Venue}.
 *
 * <p>{@link #content} and {@link #venue} are real {@code @ManyToOne}
 * associations (unlike a cross-service reference) because both
 * {@link Content} and {@link Venue} are owned by this same service.
 * {@code fetch = FetchType.LAZY} to avoid unintended eager loading. There
 * is intentionally no direct {@code Content -> Venue} relationship and no
 * direct {@code Show -> Seat} relationship — {@code shows} is the join
 * point between Content and Venue; per-show seat availability is
 * booking-service's {@code show_seats} table, referencing this entity's
 * id, not the other way around.
 *
 * <p>{@code chk_shows_end_after_start} ({@code endTime > startTime}) is a
 * database CHECK constraint, not re-declared here as a Hibernate
 * {@code @Check} annotation — consistent with how other DB-level
 * constraints (unique constraints, other CHECKs) are documented in
 * Javadoc rather than duplicated as Hibernate-specific annotations
 * elsewhere in this codebase, since {@code ddl-auto = none} means
 * Hibernate never acts on that metadata anyway.
 *
 * <p>{@code status} has no Java-level default; the DB's
 * {@code DEFAULT 'SCHEDULED'} is a safety net for non-JPA inserts only —
 * application code is expected to set it explicitly, matching how
 * {@code BookingStatus}/{@code ShowSeatStatus} are handled in
 * booking-service.
 *
 * <p>{@code createdAt}/{@code updatedAt} are {@code insertable = false,
 * updatable = false}: the database owns these values (column default and
 * the {@code trg_shows_set_updated_at} trigger), not this entity.
 */
@Entity
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** FK to content.id (fk_shows_content). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    /** FK to venues.id (fk_shows_venue). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    /** chk_shows_status: SCHEDULED, CANCELLED, COMPLETED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShowStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Show() {
        // Required by JPA. Public (not protected) so a future
        // com.eventtick.catalog.service class can construct a new Show
        // directly.
    }

    public UUID getId() {
        return id;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public ShowStatus getStatus() {
        return status;
    }

    public void setStatus(ShowStatus status) {
        this.status = status;
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
        if (!(o instanceof Show other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
