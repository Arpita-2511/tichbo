package com.eventtick.catalog.controller;

import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.ContentType;
import com.eventtick.catalog.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    private static Content content(String title, ContentType type) {
        Content c = new Content();
        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
        c.setType(type);
        c.setTitle(title);
        c.setGenre("Action");
        c.setLanguage("English");
        ReflectionTestUtils.setField(c, "createdAt", Instant.parse("2026-10-01T10:00:00Z"));
        ReflectionTestUtils.setField(c, "updatedAt", Instant.parse("2026-10-01T10:00:00Z"));
        return c;
    }

    private void mockSearchReturning(List<Content> items) {
        Pageable pageable = PageRequest.of(0, 12);
        when(searchService.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(items, pageable, items.size()));
    }

    @Test
    void search_noParams_returnsPagedResults() throws Exception {
        Content c = content("Inception", ContentType.MOVIE);
        mockSearchReturning(List.of(c));

        mockMvc.perform(get("/api/catalog/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].title", is("Inception")))
                .andExpect(jsonPath("$.results[0].type", is("MOVIE")))
                .andExpect(jsonPath("$.results[0].id").isNotEmpty())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(12)))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)));

        verify(searchService).search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 12)));
    }

    @Test
    void search_withTextQuery_passesQToService() throws Exception {
        mockSearchReturning(List.of());

        mockMvc.perform(get("/api/catalog/search").param("q", "inception"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));

        verify(searchService).search(eq("inception"), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void search_withCategory_passesEnumToService() throws Exception {
        Content c = content("IPL 2026", ContentType.SPORTS_MATCH);
        mockSearchReturning(List.of(c));

        mockMvc.perform(get("/api/catalog/search").param("category", "SPORTS_MATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].type", is("SPORTS_MATCH")));

        verify(searchService).search(isNull(), eq(ContentType.SPORTS_MATCH), isNull(), isNull(), isNull(), any());
    }

    @Test
    void search_withInvalidCategory_returns400() throws Exception {
        mockMvc.perform(get("/api/catalog/search").param("category", "INVALID_TYPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_withDate_passesLocalDateToService() throws Exception {
        mockSearchReturning(List.of());

        mockMvc.perform(get("/api/catalog/search").param("date", "2026-10-15"))
                .andExpect(status().isOk());

        verify(searchService).search(isNull(), isNull(), eq(LocalDate.of(2026, 10, 15)), isNull(), isNull(), any());
    }

    @Test
    void search_withLocation_passesCityToService() throws Exception {
        mockSearchReturning(List.of());

        mockMvc.perform(get("/api/catalog/search").param("location", "Mumbai"))
                .andExpect(status().isOk());

        verify(searchService).search(isNull(), isNull(), isNull(), eq("Mumbai"), isNull(), any());
    }

    @Test
    void search_withVenueId_passesUuidToService() throws Exception {
        UUID venueId = UUID.randomUUID();
        mockSearchReturning(List.of());

        mockMvc.perform(get("/api/catalog/search").param("venue", venueId.toString()))
                .andExpect(status().isOk());

        verify(searchService).search(isNull(), isNull(), isNull(), isNull(), eq(venueId), any());
    }

    @Test
    void search_withInvalidVenueUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/catalog/search").param("venue", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_withPagination_passesPageableToService() throws Exception {
        mockSearchReturning(List.of());

        mockMvc.perform(get("/api/catalog/search")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(searchService).search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(2, 5)));
    }

    @Test
    void search_sizeExceedsMax_clampedTo50() throws Exception {
        mockSearchReturning(List.of());

        mockMvc.perform(get("/api/catalog/search").param("size", "200"))
                .andExpect(status().isOk());

        verify(searchService).search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 50)));
    }

    @Test
    void search_sizeZeroOrNegative_clampedTo1() throws Exception {
        mockSearchReturning(List.of());

        mockMvc.perform(get("/api/catalog/search").param("size", "0"))
                .andExpect(status().isOk());

        verify(searchService).search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 1)));
    }

    @Test
    void search_negativePageNumber_clampedToZero() throws Exception {
        mockSearchReturning(List.of());

        mockMvc.perform(get("/api/catalog/search").param("page", "-1"))
                .andExpect(status().isOk());

        verify(searchService).search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 12)));
    }

    @Test
    void search_combinedFilters_allPassedToService() throws Exception {
        Content c = content("Avengers", ContentType.MOVIE);
        mockSearchReturning(List.of(c));
        UUID venueId = UUID.randomUUID();

        mockMvc.perform(get("/api/catalog/search")
                        .param("q", "avengers")
                        .param("category", "MOVIE")
                        .param("date", "2026-12-25")
                        .param("location", "Delhi")
                        .param("venue", venueId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].title", is("Avengers")));

        verify(searchService).search(
                eq("avengers"),
                eq(ContentType.MOVIE),
                eq(LocalDate.of(2026, 12, 25)),
                eq("Delhi"),
                eq(venueId),
                eq(PageRequest.of(0, 10)));
    }

    @Test
    void search_emptyResults_returnsEmptyPage() throws Exception {
        Pageable pageable = PageRequest.of(0, 12);
        when(searchService.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mockMvc.perform(get("/api/catalog/search").param("q", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)));
    }

    @Test
    void search_multipleResults_allFieldsMapped() throws Exception {
        Content c1 = content("Inception", ContentType.MOVIE);
        c1.setDescription("A mind-bending thriller");
        c1.setReleaseOrEventDate(LocalDate.of(2026, 7, 16));
        Content c2 = content("Coldplay Live", ContentType.CONCERT);
        c2.setLanguage(null);
        c2.setGenre("Rock");
        mockSearchReturning(List.of(c1, c2));

        mockMvc.perform(get("/api/catalog/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].description", is("A mind-bending thriller")))
                .andExpect(jsonPath("$.results[0].releaseOrEventDate", is("2026-07-16")))
                .andExpect(jsonPath("$.results[1].title", is("Coldplay Live")))
                .andExpect(jsonPath("$.results[1].genre", is("Rock")));
    }

    @Test
    void search_responseExcludesTimestamps() throws Exception {
        Content c = content("Inception", ContentType.MOVIE);
        mockSearchReturning(List.of(c));

        mockMvc.perform(get("/api/catalog/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.results[0].updatedAt").doesNotExist());
    }
}
