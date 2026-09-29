package com.eventtick.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to {@code booking_event_audit} (see
 * {@code database/migrations/0014_create_booking_event_audit_table.up.sql}).
 * One row per successfully consumed {@code BookingCreated} event — see
 * that migration's table comment for the full reasoning. Not a second
 * source of truth for bookings: {@code bookingId}/{@code userId}/
 * {@code showId} are plain copied references, not JPA relationships to
 * any entity this service doesn't itself own (this service owns none).
 *
 * <p>Implements {@link Persistable} for exactly the same reason {@code
 * booking-service}'s own {@code OutboxEvent} does: {@code eventId} has no
 * {@code @GeneratedValue} (it is the exact id from the consumed envelope,
 * assigned by the application, not the database), so without this,
 * Spring Data's default "is this new?" check (id == null) would always
 * say "no," causing {@code save(...)} to {@code merge} instead of
 * {@code persist} — silently upserting on a duplicate {@code eventId}
 * instead of failing the unique-constraint check idempotent consumption
 * depends on.
 */
@Entity
@Table(name = "booking_event_audit")
public class BookingEventAudit implements Persistable<UUID> {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", nullable = false, length = 255)
    private String correlationId;

    // Schema-generation hint only (matches OutboxEvent.createdAt's own
    // precedent) — inert against the real Postgres migration, which
    // already has its own DEFAULT now(); lets Hibernate's ddl-auto:
    // create-drop test schema generate a workable default, since
    // insertable = false means this entity never supplies the value.
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "processed_at", nullable = false, insertable = false, updatable = false)
    private Instant processedAt;

    @jakarta.persistence.Transient
    private boolean isNew = false;

    public BookingEventAudit() {
        // Required by JPA. isNew stays false: this constructor is what
        // Hibernate uses to reconstruct a row already in the database.
    }

    public BookingEventAudit(UUID eventId, String eventType, UUID bookingId, UUID userId, UUID showId,
                              Instant occurredAt, String correlationId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.bookingId = bookingId;
        this.userId = userId;
        this.showId = showId;
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

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getShowId() {
        return showId;
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
        if (!(o instanceof BookingEventAudit other)) {
            return false;
        }
        return eventId != null && eventId.equals(other.eventId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
