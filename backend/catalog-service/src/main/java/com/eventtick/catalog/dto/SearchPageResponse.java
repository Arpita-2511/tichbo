package com.eventtick.catalog.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record SearchPageResponse(
        List<SearchResultResponse> results,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static SearchPageResponse from(Page<SearchResultResponse> page) {
        return new SearchPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
