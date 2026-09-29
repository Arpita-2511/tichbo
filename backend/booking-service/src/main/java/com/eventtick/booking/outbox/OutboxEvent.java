package com.eventtick.booking.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to {@code booking_outbox_events} (see
 * {@code database/migrations/0013_create_booking_outbox_events_table.up.sql}).
 * One row per domain event this service has ever raised — see that
 * migration's table comment for the full transactional-outbox reasoning.
 *
 * <p>{@code eventId} is the primary key and is supplied by the application
 * (via {@link OutboxService}) when the row is constructed, not database
 * generated — there is no separate surrogate id, since the event's own
 * identity <i>is</i> this row's identity.
 *
 * <p>No {@code @ManyToOne} to {@code Booking}: {@code aggregateId} is a
 * plain {@link UUID}, deliberately not a JPA relationship, because this
 * table is generic outbox infrastructure that must be able to hold an id
 * from any future aggregate type, not only bookings (see the migration's
 * own comment on {@code aggregate_id}).
 *
 * <p>Implements {@link Persistable} because {@code eventId} has no
 * {@code @GeneratedValue}: Spring Data's default "is this entity new?"
 * check is "is the id null?", and this entity's id is never null (the
 * application always assigns one before the first save) — without this,
 * {@code repository.save(...)} would call {@code entityManager.merge(...)}
 * instead of {@code persist(...)} for every single row, silently
 * <b>upserting</b> instead of inserting. That would make a genuine
 * {@code event_id} collision overwrite the original row rather than fail
 * the unique-constraint check it's supposed to fail (found by {@code
 * OutboxEventRepositoryTest} itself). {@link #isNew} is {@code true} only
 * when set by the constructor callers actually use; an entity Hibernate
 * reconstructs from a database row (via the no-arg constructor) leaves it
 * at its default {@code false}.
 */
@Entity
@Table(name = "booking_outbox_events")
public class OutboxEvent implements Persistable<UUID> {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    /** The fully-serialized JSON envelope — see the migration's column comment. */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "published_at")
    private Instant publishedAt;

    // Schema-generation hints only (matches Payment.createdAt's own
    // precedent) — inert against the real Postgres migration, which already
    // has its own DEFAULT now() + trigger; this is what lets Hibernate's
    // ddl-auto: create-drop test schema (H2, used only by this outbox
    // feature's own dedicated tests) generate a workable default, since
    // insertable = false means this entity never supplies the value.
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /** See the class Javadoc on {@link Persistable}. Not a column — {@link Transient}. */
    @Transient
    private boolean isNew = false;

    public OutboxEvent() {
        // Required by JPA. isNew stays false: this constructor is what
        // Hibernate uses to reconstruct a row already in the database.
    }

    public OutboxEvent(UUID eventId, String eventType, String aggregateType, UUID aggregateId,
                        String topic, String payload, Instant occurredAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.payload = payload;
        this.occurredAt = occurredAt;
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

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getPublishedAt() {
        return publishedAt;
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
        if (!(o instanceof OutboxEvent other)) {
            return false;
        }
        return eventId != null && eventId.equals(other.eventId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
