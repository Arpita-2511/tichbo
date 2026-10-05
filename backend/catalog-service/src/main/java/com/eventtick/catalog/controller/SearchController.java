package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.SearchPageResponse;
import com.eventtick.catalog.dto.SearchResultResponse;
import com.eventtick.catalog.entity.ContentType;
import com.eventtick.catalog.service.SearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/search")
public class SearchController {

    private static final int MAX_PAGE_SIZE = 50;

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchPageResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ContentType category,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID venue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        int effectiveSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int effectivePage = Math.max(0, page);

        return SearchPageResponse.from(
                searchService.search(q, category, date, location, venue,
                        PageRequest.of(effectivePage, effectiveSize))
                        .map(SearchResultResponse::from));
    }
}
