package com.eventtick.user.entity;

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
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code users} table (see
 * {@code database/migrations/0002_create_users_table.up.sql}).
 *
 * <p>{@link #plan} is a real {@code @ManyToOne} (unlike, say, a Booking
 * Service reference to a user) because {@code plans} is owned by this
 * same service — matching the "same-service FK = real association"
 * pattern already used for {@code Show.content}/{@code Show.venue} in
 * catalog-service. {@code fetch = FetchType.LAZY} to avoid unintended
 * eager loading. {@code plan_id} has no database default (see the
 * column's own DB comment) — a plan must be resolved and set explicitly
 * by {@code AuthService} at registration.
 *
 * <p>{@link #passwordHash} exists only to be read internally by
 * {@code AuthService} for verification (via {@code PasswordEncoder}) —
 * it must never be serialized into a DTO or logged. No response DTO in
 * this service includes it.
 *
 * <p>{@code createdAt}/{@code updatedAt} are {@code insertable = false,
 * updatable = false}: the database owns these values (column default and
 * the {@code trg_users_set_updated_at} trigger), not this entity.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Must be lowercased by the caller before being set here — the
     * database does not enforce casing (see the column's own DB comment),
     * so {@code AuthService} is responsible for normalizing it.
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** chk_users_role: CUSTOMER, ADMIN. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /** FK to plans.id (fk_users_plan). No database default — see class Javadoc. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // See Plan.createdAt's comment: schema-generation hint only, inert in
    // production (ddl-auto: none), needed so Hibernate's auto-generated
    // H2 test schema gives this NOT NULL column a default.
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public User() {
        // Required by JPA. Public (not protected) so AuthService can
        // construct a new User directly — same lesson learned in
        // booking-service's Booking/BookingSeat entities.
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
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
        if (!(o instanceof User other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
