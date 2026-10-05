package com.eventtick.catalog.dto;

import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.ContentType;

import java.time.LocalDate;
import java.util.UUID;

public record SearchResultResponse(
        UUID id,
        ContentType type,
        String title,
        String description,
        String language,
        String genre,
        LocalDate releaseOrEventDate
) {

    public static SearchResultResponse from(Content content) {
        return new SearchResultResponse(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getDescription(),
                content.getLanguage(),
                content.getGenre(),
                content.getReleaseOrEventDate());
    }
}
