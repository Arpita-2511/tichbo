package com.eventtick.catalog.dto;

import com.eventtick.catalog.entity.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Request body for creating or updating a {@link com.eventtick.catalog.entity.Content}.
 * One shape for both — {@code ContentService.create}/{@code update} take
 * identical parameters, unlike Show (see {@link ShowUpdateRequest}).
 *
 * <p>No {@code id}/{@code createdAt}/{@code updatedAt} — those are
 * database-generated/managed, never client-supplied.
 */
public record ContentRequest(
        @NotNull ContentType type,
        @NotBlank String title,
        String description,
        String language,
        @Positive Integer duration,
        String genre,
        LocalDate releaseOrEventDate
) {
}
