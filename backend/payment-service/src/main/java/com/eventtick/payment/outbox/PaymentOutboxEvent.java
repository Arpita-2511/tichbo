package com.eventtick.payment.outbox;

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
 * Maps to {@code payment_outbox_events} (see
 * {@code database/migrations/0015_create_payment_outbox_events_table.up.sql}).
 * One row per domain event this service has ever raised — payment-service's
 * own copy of booking-service's {@code OutboxEvent}, field-for-field
 * identical, deliberately a separate table (see that migration's own
 * comment) rather than a shared one across services.
 *
 * <p>Implements {@link Persistable} for exactly the reason
 * booking-service's own {@code OutboxEvent} does: {@code eventId} has no
 * {@code @GeneratedValue} (the application always assigns one before the
 * first save), so without this, {@code repository.save(...)} would
 * {@code merge} instead of {@code persist}, silently upserting instead of
 * failing the unique-constraint check on a genuine {@code event_id}
 * collision.
 */
@Entity
@Table(name = "payment_outbox_events")
public class PaymentOutboxEvent implements Persistable<UUID> {

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

    // Schema-generation hints only (matches OutboxEvent.createdAt's own
    // precedent) — inert against the real Postgres migration; lets
    // Hibernate's ddl-auto: create-drop test schema (H2, this feature's
    // own dedicated tests) generate a workable default.
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /** See the class Javadoc on {@link Persistable}. Not a column — {@link Transient}. */
    @Transient
    private boolean isNew = false;

    public PaymentOutboxEvent() {
        // Required by JPA. isNew stays false: this constructor is what
        // Hibernate uses to reconstruct a row already in the database.
    }

    public PaymentOutboxEvent(UUID eventId, String eventType, String aggregateType, UUID aggregateId,
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
        if (!(o instanceof PaymentOutboxEvent other)) {
            return false;
        }
        return eventId != null && eventId.equals(other.eventId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
