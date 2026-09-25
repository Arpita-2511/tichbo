package com.eventtick.catalog.controller;

import com.eventtick.catalog.service.ContentService;
import com.eventtick.catalog.service.ShowService;
import com.eventtick.catalog.service.VenueService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13, FR-36: {@code GET /api/admin/content/stats} — the
 * catalog-service portion of Admin Overview and Statistics. A
 * {@code @WebMvcTest} slice with all three of {@link AdminCatalogStatsController}'s
 * dependencies mocked — catalog-service has no schema-generation mechanism
 * for its H2 test database ({@code ddl-auto: none}), so a real
 * repository-level count isn't available to this test suite. Nothing about
 * this endpoint's own logic needs one anyway: {@code ContentService}/
 * {@code ShowService}/{@code VenueService}'s new {@code countAll()}
 * methods are each a one-line {@code repository.count()} delegation, the
 * same triviality {@code UserService.countAll()} already has (Phase 13,
 * FR-36's user-stats slice).
 */
@WebMvcTest(AdminCatalogStatsController.class)
class AdminCatalogStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContentService contentService;

    @MockBean
    private ShowService showService;

    @MockBean
    private VenueService venueService;

    @Test
    void validRequest_returns200() throws Exception {
        when(contentService.countAll()).thenReturn(5L);
        when(showService.countAll()).thenReturn(12L);
        when(venueService.countAll()).thenReturn(3L);

        mockMvc.perform(get("/api/admin/content/stats"))
                .andExpect(status().isOk());
    }

    @Test
    void totalContent_reflectsContentServiceCount() throws Exception {
        when(contentService.countAll()).thenReturn(7L);
        when(showService.countAll()).thenReturn(0L);
        when(venueService.countAll()).thenReturn(0L);

        mockMvc.perform(get("/api/admin/content/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalContent").value(7));
    }

    @Test
    void totalShows_reflectsShowServiceCount() throws Exception {
        when(contentService.countAll()).thenReturn(0L);
        when(showService.countAll()).thenReturn(9L);
        when(venueService.countAll()).thenReturn(0L);

        mockMvc.perform(get("/api/admin/content/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShows").value(9));
    }

    @Test
    void totalVenues_reflectsVenueServiceCount() throws Exception {
        when(contentService.countAll()).thenReturn(0L);
        when(showService.countAll()).thenReturn(0L);
        when(venueService.countAll()).thenReturn(4L);

        mockMvc.perform(get("/api/admin/content/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVenues").value(4));
    }

    @Test
    void emptyDatabase_allThreeValuesAreZero() throws Exception {
        when(contentService.countAll()).thenReturn(0L);
        when(showService.countAll()).thenReturn(0L);
        when(venueService.countAll()).thenReturn(0L);

        mockMvc.perform(get("/api/admin/content/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalContent").value(0))
                .andExpect(jsonPath("$.totalShows").value(0))
                .andExpect(jsonPath("$.totalVenues").value(0));
    }

    @Test
    void response_containsExactlyTheThreeExpectedFields_noUnrelatedEntityData() throws Exception {
        when(contentService.countAll()).thenReturn(1L);
        when(showService.countAll()).thenReturn(2L);
        when(venueService.countAll()).thenReturn(3L);

        String body = mockMvc.perform(get("/api/admin/content/stats"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        Set<String> fieldNames = new LinkedHashSet<>();
        for (Iterator<String> it = json.fieldNames(); it.hasNext(); ) {
            fieldNames.add(it.next());
        }
        // Exactly the CatalogStatsResponse record components — no
        // per-entity data (titles, ids, addresses, etc.), no admin-specific
        // extra field.
        assertThat(fieldNames).containsExactlyInAnyOrder("totalContent", "totalShows", "totalVenues");
    }
}
