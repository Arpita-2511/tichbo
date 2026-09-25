package com.eventtick.catalog.controller;

import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.entity.ShowStatus;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.exception.CatalogEntityNotFoundException;
import com.eventtick.catalog.service.ShowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.6.2: {@code PATCH /api/admin/shows/{id}/cancel} — the HTTP
 * contract (status codes, response shape, correct delegation to
 * {@link ShowService#cancel}, the existing 404 behavior). See
 * {@code ShowServiceCancelTest} for what {@link ShowService#cancel} itself
 * actually does to {@code content}/{@code venue}/{@code startTime}/
 * {@code endTime} — a {@code @WebMvcTest} mocks the service, so it cannot
 * prove that.
 */
@WebMvcTest(AdminShowController.class)
class AdminShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShowService showService;

    private static Show cancelledShow(UUID id) {
        Content content = new Content();
        ReflectionTestUtils.setField(content, "id", UUID.randomUUID());
        Venue venue = new Venue();
        ReflectionTestUtils.setField(venue, "id", UUID.randomUUID());

        Show show = new Show();
        ReflectionTestUtils.setField(show, "id", id);
        show.setContent(content);
        show.setVenue(venue);
        show.setStartTime(Instant.parse("2026-10-01T18:00:00Z"));
        show.setEndTime(Instant.parse("2026-10-01T20:30:00Z"));
        show.setStatus(ShowStatus.CANCELLED);
        ReflectionTestUtils.setField(show, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(show, "updatedAt", Instant.parse("2026-09-25T12:00:00Z"));
        return show;
    }

    @Test
    void cancel_returns200_withTheExistingShowResponseShape_showingCancelledStatus() throws Exception {
        UUID id = UUID.randomUUID();
        Show show = cancelledShow(id);
        when(showService.cancel(id)).thenReturn(show);

        mockMvc.perform(patch("/api/admin/shows/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.contentId").value(show.getContent().getId().toString()))
                .andExpect(jsonPath("$.venueId").value(show.getVenue().getId().toString()))
                .andExpect(jsonPath("$.startTime").exists())
                .andExpect(jsonPath("$.endTime").exists());

        verify(showService).cancel(eq(id));
    }

    @Test
    void cancel_takesNoRequestBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(showService.cancel(id)).thenReturn(cancelledShow(id));

        // No .content(...)/.contentType(...) at all — the endpoint has no request DTO.
        mockMvc.perform(patch("/api/admin/shows/" + id + "/cancel"))
                .andExpect(status().isOk());
    }

    @Test
    void cancel_unknownId_returnsTheExisting404ErrorFormat() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(showService.cancel(unknownId)).thenThrow(new CatalogEntityNotFoundException("Show", unknownId));

        mockMvc.perform(patch("/api/admin/shows/" + unknownId + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Show not found: " + unknownId));
    }

    @Test
    void cancel_malformedId_returnsTheExisting400Behavior() throws Exception {
        mockMvc.perform(patch("/api/admin/shows/not-a-uuid/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
