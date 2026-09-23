package com.eventtick.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code venues} table (see
 * {@code database/migrations/0005_create_venues_table.up.sql}) — the
 * physical location where a {@link Show} takes place.
 *
 * <p>No {@code @OneToMany} collections of {@link Seat}s or {@link Show}s
 * are declared here — same reasoning as {@link Content}'s Javadoc:
 * navigating from a Venue to its seats/shows should go through a future
 * repository query, not an inverse collection whose fetch/cascade
 * behavior this entity-only pass shouldn't decide silently.
 *
 * <p>{@code createdAt}/{@code updatedAt} are {@code insertable = false,
 * updatable = false}: the database owns these values (column default and
 * the {@code trg_venues_set_updated_at} trigger), not this entity.
 */
@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Venue() {
        // Required by JPA. Public (not protected) so a future
        // com.eventtick.catalog.service class can construct a new Venue
        // directly.
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
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
        if (!(o instanceof Venue other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
