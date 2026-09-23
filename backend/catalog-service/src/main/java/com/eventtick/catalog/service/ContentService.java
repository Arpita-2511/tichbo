package com.eventtick.catalog.service;

import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.ContentType;
import com.eventtick.catalog.exception.CatalogEntityNotFoundException;
import com.eventtick.catalog.repository.ContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Persistence and basic validation for {@link Content} (the
 * {@code content} table). No relationships to other Catalog entities —
 * unlike {@link Seat}/{@link Show}, creating or updating a Content never
 * needs to resolve another entity first.
 */
@Service
public class ContentService {

    private final ContentRepository contentRepository;

    public ContentService(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Transactional
    public Content create(ContentType type, String title, String description, String language,
                           Integer duration, String genre, LocalDate releaseOrEventDate) {
        Content content = new Content();
        content.setType(type);
        content.setTitle(title);
        content.setDescription(description);
        content.setLanguage(language);
        content.setDuration(duration);
        content.setGenre(genre);
        content.setReleaseOrEventDate(releaseOrEventDate);
        validate(content);
        return contentRepository.save(content);
    }

    @Transactional(readOnly = true)
    public Content getById(UUID id) {
        return contentRepository.findById(id)
                .orElseThrow(() -> new CatalogEntityNotFoundException("Content", id));
    }

    @Transactional(readOnly = true)
    public List<Content> list() {
        return contentRepository.findAll();
    }

    /** Full replace of every mutable field — there is no partial-update variant without a DTO layer to define "unset". */
    @Transactional
    public Content update(UUID id, ContentType type, String title, String description, String language,
                           Integer duration, String genre, LocalDate releaseOrEventDate) {
        Content content = getById(id);
        content.setType(type);
        content.setTitle(title);
        content.setDescription(description);
        content.setLanguage(language);
        content.setDuration(duration);
        content.setGenre(genre);
        content.setReleaseOrEventDate(releaseOrEventDate);
        validate(content);
        return contentRepository.save(content);
    }

    /**
     * Deletes a Content row. Will fail at the database level
     * ({@code fk_shows_content ON DELETE RESTRICT}) if any Show still
     * references it — not pre-checked here; see class Javadoc discussion
     * in {@code ShowService} for why that's left to the DB constraint.
     */
    @Transactional
    public void delete(UUID id) {
        contentRepository.delete(getById(id));
    }

    private void validate(Content content) {
        if (content.getType() == null) {
            throw new IllegalArgumentException("Content type is required.");
        }
        if (content.getTitle() == null || content.getTitle().isBlank()) {
            throw new IllegalArgumentException("Content title is required.");
        }
        if (content.getDuration() != null && content.getDuration() <= 0) {
            throw new IllegalArgumentException("Content duration must be positive.");
        }
    }
}
