package com.eventtick.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code plans} table (see
 * {@code database/migrations/0001_create_plans_table.up.sql}). No
 * {@code @OneToMany} collection of {@link User}s — same reasoning used
 * throughout this codebase (Content/Venue/Booking all omit their inverse
 * collections too): that's a future service-layer decision, not one to
 * make silently here.
 *
 * <p>{@code createdAt}/{@code updatedAt} are {@code insertable = false,
 * updatable = false}: the database owns these values (column default and
 * the {@code trg_plans_set_updated_at} trigger), not this entity.
 */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "description")
    private String description;

    // @ColumnDefault is schema-generation metadata only — it has zero
    // effect on INSERT/UPDATE statements (insertable/updatable stay
    // false) and zero effect on the real database (ddl-auto: none means
    // Hibernate never generates DDL there). It exists purely so
    // Hibernate's auto-generated H2 test schema (ddl-auto: create-drop,
    // see src/test/resources/application.yml) gives these NOT NULL
    // columns a default — without it, nothing supplies a value on
    // insert and every test-database write fails.
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Plan() {
        // Required by JPA.
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
        if (!(o instanceof Plan other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
