package com.eventtick.catalog.dto;

import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.ContentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContentResponse(
        UUID id,
        ContentType type,
        String title,
        String description,
        String language,
        Integer duration,
        String genre,
        LocalDate releaseOrEventDate,
        Instant createdAt,
        Instant updatedAt
) {

    public static ContentResponse from(Content content) {
        return new ContentResponse(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getDescription(),
                content.getLanguage(),
                content.getDuration(),
                content.getGenre(),
                content.getReleaseOrEventDate(),
                content.getCreatedAt(),
                content.getUpdatedAt());
    }
}
