package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ShowCreateRequest;
import com.eventtick.catalog.dto.ShowUpdateRequest;
import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.entity.ShowStatus;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.service.ShowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.6.2: proves the five existing {@code /api/catalog/shows} routes
 * still resolve and behave exactly as before, now that
 * {@link AdminShowController} exists alongside {@link ShowController} (a
 * separate class specifically so the two controllers' route tables can't
 * collide — see {@link AdminShowController}'s Javadoc). Mirrors
 * {@code ContentControllerRegressionTest} (Phase 13.5.2).
 */
@WebMvcTest(ShowController.class)
class ShowControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShowService showService;

    private static Show sampleShow() {
        Content content = new Content();
        ReflectionTestUtils.setField(content, "id", UUID.randomUUID());
        Venue venue = new Venue();
        ReflectionTestUtils.setField(venue, "id", UUID.randomUUID());

        Show show = new Show();
        ReflectionTestUtils.setField(show, "id", UUID.randomUUID());
        show.setContent(content);
        show.setVenue(venue);
        show.setStartTime(Instant.parse("2026-10-01T18:00:00Z"));
        show.setEndTime(Instant.parse("2026-10-01T20:30:00Z"));
        show.setStatus(ShowStatus.SCHEDULED);
        ReflectionTestUtils.setField(show, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(show, "updatedAt", Instant.parse("2026-09-25T10:00:00Z"));
        return show;
    }

    @Test
    void postApiCatalogShows_stillCreates() throws Exception {
        Show saved = sampleShow();
        when(showService.create(any(), any(), any(), any())).thenReturn(saved);
        ShowCreateRequest request = new ShowCreateRequest(saved.getContent().getId(), saved.getVenue().getId(),
                saved.getStartTime(), saved.getEndTime());

        mockMvc.perform(post("/api/catalog/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/catalog/shows/" + saved.getId()));
    }

    @Test
    void getApiCatalogShows_stillLists() throws Exception {
        when(showService.list()).thenReturn(List.of(sampleShow()));

        mockMvc.perform(get("/api/catalog/shows"))
                .andExpect(status().isOk());
    }

    @Test
    void getApiCatalogShowsById_stillReturnsOne() throws Exception {
        Show show = sampleShow();
        when(showService.getById(show.getId())).thenReturn(show);

        mockMvc.perform(get("/api/catalog/shows/" + show.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void putApiCatalogShowsById_stillUpdates() throws Exception {
        Show updated = sampleShow();
        when(showService.update(any(), any(), any(), any(), any(), any())).thenReturn(updated);
        ShowUpdateRequest request = new ShowUpdateRequest(updated.getContent().getId(), updated.getVenue().getId(),
                updated.getStartTime(), updated.getEndTime(), ShowStatus.COMPLETED);

        mockMvc.perform(put("/api/catalog/shows/" + updated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteApiCatalogShowsById_stillDeletes() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/catalog/shows/" + id))
                .andExpect(status().isNoContent());
    }
}
