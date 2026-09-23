package com.eventtick.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Maps to the {@code content} table (see
 * {@code database/migrations/0004_create_content_table.up.sql}) — the
 * generic entity representing anything bookable (a movie, sports match,
 * concert, theatre production, or other event).
 *
 * <p>No {@code @OneToMany} collection of {@link Show}s is declared here.
 * {@link Show} owns the {@code content_id} foreign key; navigating from a
 * Content to its Shows should go through a future
 * {@code ShowRepository.findByContentId} query, not an eagerly-designed
 * inverse collection with fetch/cascade decisions this entity-only pass
 * shouldn't make silently.
 *
 * <p>{@code createdAt}/{@code updatedAt} are {@code insertable = false,
 * updatable = false}: the database owns these values (column default and
 * the {@code trg_content_set_updated_at} trigger), not this entity.
 */
@Entity
@Table(name = "content")
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** chk_content_type: MOVIE, SPORTS_MATCH, CONCERT, THEATRE, EVENT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ContentType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "language", length = 50)
    private String language;

    /** Runtime in minutes, where applicable. Nullable — not every content type has one. */
    @Column(name = "duration")
    private Integer duration;

    @Column(name = "genre", length = 100)
    private String genre;

    /**
     * Coarse date associated with the content (e.g. a movie's release
     * date). Nullable — precise per-occurrence scheduling lives on
     * {@link Show#getStartTime()}/{@link Show#getEndTime()}, not here.
     */
    @Column(name = "release_or_event_date")
    private LocalDate releaseOrEventDate;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public Content() {
        // Required by JPA. Public (not protected) so a future
        // com.eventtick.catalog.service class can construct a new Content
        // directly.
    }

    public UUID getId() {
        return id;
    }

    public ContentType getType() {
        return type;
    }

    public void setType(ContentType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public LocalDate getReleaseOrEventDate() {
        return releaseOrEventDate;
    }

    public void setReleaseOrEventDate(LocalDate releaseOrEventDate) {
        this.releaseOrEventDate = releaseOrEventDate;
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
        if (!(o instanceof Content other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
