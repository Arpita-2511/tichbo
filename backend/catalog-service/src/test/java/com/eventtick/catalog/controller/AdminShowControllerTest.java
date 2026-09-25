package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ShowCreateRequest;
import com.eventtick.catalog.dto.ShowUpdateRequest;
import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.entity.ShowStatus;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.exception.CatalogEntityNotFoundException;
import com.eventtick.catalog.service.ShowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.6.2 ({@code PATCH .../cancel}) / FR-38 ({@code POST}, {@code PUT}):
 * {@code /api/admin/shows}. The HTTP contract (status codes, response
 * shape, correct delegation to {@link ShowService}, the existing 404/400
 * behavior). See {@code ShowServiceCancelTest} for what
 * {@link ShowService#cancel} itself actually does to {@code content}/
 * {@code venue}/{@code startTime}/{@code endTime} — a {@code @WebMvcTest}
 * mocks the service, so it cannot prove that.
 */
@WebMvcTest(AdminShowController.class)
class AdminShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private static Show scheduledShow(UUID id, UUID contentId, UUID venueId, Instant start, Instant end) {
        Content content = new Content();
        ReflectionTestUtils.setField(content, "id", contentId);
        Venue venue = new Venue();
        ReflectionTestUtils.setField(venue, "id", venueId);

        Show show = new Show();
        ReflectionTestUtils.setField(show, "id", id);
        show.setContent(content);
        show.setVenue(venue);
        show.setStartTime(start);
        show.setEndTime(end);
        show.setStatus(ShowStatus.SCHEDULED);
        ReflectionTestUtils.setField(show, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(show, "updatedAt", Instant.parse("2026-09-25T10:00:00Z"));
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

    // ==================== POST /api/admin/shows ====================

    @Test
    void create_validRequest_reachesShowServiceCreate_andReturns201WithShowResponse() throws Exception {
        UUID contentId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T18:00:00Z");
        Instant end = Instant.parse("2026-10-01T20:30:00Z");
        ShowCreateRequest request = new ShowCreateRequest(contentId, venueId, start, end);
        Show saved = scheduledShow(UUID.randomUUID(), contentId, venueId, start, end);
        when(showService.create(contentId, venueId, start, end)).thenReturn(saved);

        mockMvc.perform(post("/api/admin/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/catalog/shows/" + saved.getId()))
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.contentId").value(contentId.toString()))
                .andExpect(jsonPath("$.venueId").value(venueId.toString()));
    }

    @Test
    void create_requestFieldsAreForwardedExactly_toShowServiceCreate() throws Exception {
        UUID contentId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-05T09:00:00Z");
        Instant end = Instant.parse("2026-11-05T11:00:00Z");
        ShowCreateRequest request = new ShowCreateRequest(contentId, venueId, start, end);
        when(showService.create(any(), any(), any(), any()))
                .thenReturn(scheduledShow(UUID.randomUUID(), contentId, venueId, start, end));

        mockMvc.perform(post("/api/admin/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(showService).create(contentId, venueId, start, end);
    }

    @Test
    void create_missingRequiredField_returns400_andNeverReachesTheService() throws Exception {
        // contentId omitted — @NotNull on ShowCreateRequest.
        String jsonWithoutContentId = "{\"venueId\":\"" + UUID.randomUUID()
                + "\",\"startTime\":\"2026-10-01T18:00:00Z\",\"endTime\":\"2026-10-01T20:30:00Z\"}";

        mockMvc.perform(post("/api/admin/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithoutContentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(showService, never()).create(any(), any(), any(), any());
    }

    @Test
    void create_referencedContentNotFound_returnsTheExisting404Behavior() throws Exception {
        UUID unknownContentId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T18:00:00Z");
        Instant end = Instant.parse("2026-10-01T20:30:00Z");
        when(showService.create(eq(unknownContentId), eq(venueId), eq(start), eq(end)))
                .thenThrow(new CatalogEntityNotFoundException("Content", unknownContentId));

        ShowCreateRequest request = new ShowCreateRequest(unknownContentId, venueId, start, end);

        mockMvc.perform(post("/api/admin/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Content not found: " + unknownContentId));
    }

    @Test
    void create_referencedVenueNotFound_returnsTheExisting404Behavior() throws Exception {
        UUID contentId = UUID.randomUUID();
        UUID unknownVenueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T18:00:00Z");
        Instant end = Instant.parse("2026-10-01T20:30:00Z");
        when(showService.create(eq(contentId), eq(unknownVenueId), eq(start), eq(end)))
                .thenThrow(new CatalogEntityNotFoundException("Venue", unknownVenueId));

        ShowCreateRequest request = new ShowCreateRequest(contentId, unknownVenueId, start, end);

        mockMvc.perform(post("/api/admin/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Venue not found: " + unknownVenueId));
    }

    // ==================== PUT /api/admin/shows/{id} ====================

    private static ShowUpdateRequest updateRequest(UUID contentId, UUID venueId, Instant start, Instant end,
                                                     ShowStatus status) {
        return new ShowUpdateRequest(contentId, venueId, start, end, status);
    }

    @Test
    void update_validRequest_reachesShowServiceUpdate_andReturns200WithShowResponse() throws Exception {
        UUID id = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T18:00:00Z");
        Instant end = Instant.parse("2026-10-01T20:30:00Z");
        ShowUpdateRequest request = updateRequest(contentId, venueId, start, end, ShowStatus.COMPLETED);
        Show updated = scheduledShow(id, contentId, venueId, start, end);
        updated.setStatus(ShowStatus.COMPLETED);
        when(showService.update(id, contentId, venueId, start, end, ShowStatus.COMPLETED)).thenReturn(updated);

        mockMvc.perform(put("/api/admin/shows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.contentId").value(contentId.toString()))
                .andExpect(jsonPath("$.venueId").value(venueId.toString()));
    }

    @Test
    void update_requestFieldsAreForwardedExactly_toShowServiceUpdate_withTheCorrectId() throws Exception {
        UUID id = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-05T09:00:00Z");
        Instant end = Instant.parse("2026-11-05T11:00:00Z");
        ShowUpdateRequest request = updateRequest(contentId, venueId, start, end, ShowStatus.SCHEDULED);
        when(showService.update(any(), any(), any(), any(), any(), any()))
                .thenReturn(scheduledShow(id, contentId, venueId, start, end));

        mockMvc.perform(put("/api/admin/shows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(showService).update(id, contentId, venueId, start, end, ShowStatus.SCHEDULED);
    }

    @Test
    void update_missingRequiredField_returns400_andNeverReachesTheService() throws Exception {
        UUID id = UUID.randomUUID();
        // status omitted — @NotNull on ShowUpdateRequest.
        String jsonWithoutStatus = "{\"contentId\":\"" + UUID.randomUUID() + "\",\"venueId\":\""
                + UUID.randomUUID() + "\",\"startTime\":\"2026-10-01T18:00:00Z\","
                + "\"endTime\":\"2026-10-01T20:30:00Z\"}";

        mockMvc.perform(put("/api/admin/shows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithoutStatus))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(showService, never()).update(any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_unknownShowId_returns404_withTheExistingEntityNotFoundBehavior() throws Exception {
        UUID unknownId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T18:00:00Z");
        Instant end = Instant.parse("2026-10-01T20:30:00Z");
        when(showService.update(eq(unknownId), any(), any(), any(), any(), any()))
                .thenThrow(new CatalogEntityNotFoundException("Show", unknownId));

        mockMvc.perform(put("/api/admin/shows/{id}", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                updateRequest(contentId, venueId, start, end, ShowStatus.SCHEDULED))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Show not found: " + unknownId));
    }

    @Test
    void update_referencedContentNotFound_returnsTheExisting404Behavior() throws Exception {
        UUID id = UUID.randomUUID();
        UUID unknownContentId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T18:00:00Z");
        Instant end = Instant.parse("2026-10-01T20:30:00Z");
        when(showService.update(eq(id), eq(unknownContentId), eq(venueId), eq(start), eq(end), any()))
                .thenThrow(new CatalogEntityNotFoundException("Content", unknownContentId));

        mockMvc.perform(put("/api/admin/shows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                updateRequest(unknownContentId, venueId, start, end, ShowStatus.SCHEDULED))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Content not found: " + unknownContentId));
    }

    @Test
    void update_referencedVenueNotFound_returnsTheExisting404Behavior() throws Exception {
        UUID id = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID unknownVenueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T18:00:00Z");
        Instant end = Instant.parse("2026-10-01T20:30:00Z");
        when(showService.update(eq(id), eq(contentId), eq(unknownVenueId), eq(start), eq(end), any()))
                .thenThrow(new CatalogEntityNotFoundException("Venue", unknownVenueId));

        mockMvc.perform(put("/api/admin/shows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                updateRequest(contentId, unknownVenueId, start, end, ShowStatus.SCHEDULED))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Venue not found: " + unknownVenueId));
    }

    @Test
    void update_invalidTimeRelationship_returnsTheExisting400Behavior() throws Exception {
        // Mirrors ShowService.update's own validateTimes check
        // (endTime must be after startTime) -> IllegalArgumentException ->
        // the existing VALIDATION_ERROR mapping.
        UUID id = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T20:30:00Z");
        Instant end = Instant.parse("2026-10-01T18:00:00Z");
        when(showService.update(eq(id), eq(contentId), eq(venueId), eq(start), eq(end), any()))
                .thenThrow(new IllegalArgumentException("Show end time must be after start time."));

        mockMvc.perform(put("/api/admin/shows/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                updateRequest(contentId, venueId, start, end, ShowStatus.SCHEDULED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Show end time must be after start time."));
    }

    // ==================== DELETE /api/admin/shows/{id} ====================

    @Test
    void delete_validId_reachesShowServiceDelete_andReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(showService).delete(id);

        mockMvc.perform(delete("/api/admin/shows/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_forwardsTheCorrectIdToShowService() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/shows/{id}", id))
                .andExpect(status().isNoContent());

        verify(showService).delete(id);
    }

    @Test
    void delete_unknownId_returns404_withTheExistingEntityNotFoundBehavior() throws Exception {
        UUID unknownId = UUID.randomUUID();
        doThrow(new CatalogEntityNotFoundException("Show", unknownId)).when(showService).delete(unknownId);

        mockMvc.perform(delete("/api/admin/shows/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Show not found: " + unknownId));
    }

    @Test
    void delete_fkConflict_returns409_withTheExistingDataIntegrityConflictBehavior() throws Exception {
        // A Show still referenced by booking-service's show_seats/bookings
        // (ON DELETE RESTRICT) — not pre-checked by ShowService.delete,
        // left to the existing DataIntegrityViolationException handler.
        UUID id = UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("fk_show_seats_show")).when(showService).delete(id);

        mockMvc.perform(delete("/api/admin/shows/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DATA_INTEGRITY_CONFLICT"));
    }

    @Test
    void delete_malformedId_returnsTheExisting400Behavior() throws Exception {
        mockMvc.perform(delete("/api/admin/shows/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(showService, never()).delete(any());
    }
}
