package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.VenueRequest;
import com.eventtick.catalog.entity.Venue;
import com.eventtick.catalog.exception.CatalogEntityNotFoundException;
import com.eventtick.catalog.service.VenueService;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-38: {@code POST}/{@code PUT /api/admin/venues}. A {@code @WebMvcTest}
 * slice — real {@link org.springframework.web.bind.annotation.RestControllerAdvice}
 * (so bean-validation/error-mapping behavior is the genuine, unmodified
 * {@code GlobalExceptionHandler}), real Jakarta Bean Validation, but a
 * mocked {@link VenueService} — catalog-service has no schema-generation
 * mechanism for its H2 test database ({@code ddl-auto: none} even for
 * tests), so a real end-to-end repository write isn't available to this
 * test suite. Nothing about these endpoints' own logic needs one anyway:
 * they delegate entirely to the existing, already-implemented
 * {@code VenueService.create}/{@code update}.
 *
 * <p>No conflict/409 test: {@code venues} has no unique constraint of any
 * kind (see {@code database/migrations/0005_create_venues_table.up.sql}),
 * so there is no existing conflict behavior to reuse or preserve here.
 *
 * <p>No {@code VenueControllerRegressionTest} companion — this phase adds
 * no existing-route regression coverage because {@link VenueController}/
 * {@link VenueService} are unmodified (see the FR-38 inspection report,
 * which already noted no such test exists for {@link VenueController} at
 * all; adding one is out of scope for this admin-only slice).
 */
@WebMvcTest(AdminVenueController.class)
class AdminVenueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VenueService venueService;

    private static VenueRequest validRequest() {
        return new VenueRequest("PVR XYZ", "123 Main Street", "Springfield");
    }

    private static Venue savedVenue(VenueRequest request) {
        Venue venue = new Venue();
        ReflectionTestUtils.setField(venue, "id", UUID.randomUUID());
        venue.setName(request.name());
        venue.setAddress(request.address());
        venue.setCity(request.city());
        ReflectionTestUtils.setField(venue, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(venue, "updatedAt", Instant.parse("2026-09-25T10:00:00Z"));
        return venue;
    }

    // ---- A: a valid request reaches VenueService and returns 201 ----

    @Test
    void validRequest_reachesVenueService_andReturns201() throws Exception {
        VenueRequest request = validRequest();
        Venue saved = savedVenue(request);
        when(venueService.create(request.name(), request.address(), request.city())).thenReturn(saved);

        mockMvc.perform(post("/api/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/catalog/venues/" + saved.getId()))
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.name").value("PVR XYZ"));

        // Exactly the existing VenueService.create — no new/duplicated logic.
        verify(venueService).create(request.name(), request.address(), request.city());
    }

    // ---- B: the response is exactly VenueResponse, nothing more/less ----

    @Test
    void response_isExactlyTheVenueResponseShape() throws Exception {
        VenueRequest request = validRequest();
        Venue saved = savedVenue(request);
        when(venueService.create(any(), any(), any())).thenReturn(saved);

        String body = mockMvc.perform(post("/api/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        Set<String> fieldNames = new LinkedHashSet<>();
        for (Iterator<String> it = json.fieldNames(); it.hasNext(); ) {
            fieldNames.add(it.next());
        }
        // The exact VenueResponse record components — no admin-specific
        // extra field, no accidental leak of anything else.
        assertThat(fieldNames).containsExactlyInAnyOrder("id", "name", "address", "city", "createdAt", "updatedAt");
    }

    // ---- C: an invalid request gets the existing validation error format ----

    @Test
    void blankName_returns400_withTheExistingValidationErrorFormat() throws Exception {
        VenueRequest invalid = new VenueRequest("", "123 Main Street", "Springfield");

        mockMvc.perform(post("/api/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(venueService, never()).create(any(), any(), any());
    }

    @Test
    void missingAddress_returns400() throws Exception {
        // address is @NotBlank on VenueRequest — omit it entirely.
        String jsonWithoutAddress = "{\"name\":\"PVR XYZ\",\"city\":\"Springfield\"}";

        mockMvc.perform(post("/api/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithoutAddress))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(venueService, never()).create(any(), any(), any());
    }

    @Test
    void blankCity_returns400() throws Exception {
        VenueRequest invalid = new VenueRequest("PVR XYZ", "123 Main Street", "");

        mockMvc.perform(post("/api/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ==================== PUT /api/admin/venues/{id} ====================

    @Test
    void putValidRequest_reachesVenueServiceUpdate_andReturns200WithVenueResponse() throws Exception {
        VenueRequest request = validRequest();
        Venue updated = savedVenue(request);
        UUID id = updated.getId();
        when(venueService.update(eq(id), eq(request.name()), eq(request.address()), eq(request.city())))
                .thenReturn(updated);

        mockMvc.perform(put("/api/admin/venues/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("PVR XYZ"));
    }

    @Test
    void putRequest_forwardsTheCorrectIdAndFields_toVenueService() throws Exception {
        VenueRequest request = new VenueRequest("Grand Theatre", "456 Elm Street", "Shelbyville");
        UUID id = UUID.randomUUID();
        when(venueService.update(any(), any(), any(), any())).thenReturn(savedVenue(request));

        mockMvc.perform(put("/api/admin/venues/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(venueService).update(id, request.name(), request.address(), request.city());
    }

    @Test
    void put_blankName_returns400_withTheExistingValidationErrorFormat_andNeverReachesTheService() throws Exception {
        VenueRequest invalid = new VenueRequest("", "123 Main Street", "Springfield");

        mockMvc.perform(put("/api/admin/venues/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(venueService, never()).update(any(), any(), any(), any());
    }

    @Test
    void put_unknownId_returns404_withTheExistingEntityNotFoundBehavior() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(venueService.update(eq(unknownId), any(), any(), any()))
                .thenThrow(new CatalogEntityNotFoundException("Venue", unknownId));

        mockMvc.perform(put("/api/admin/venues/{id}", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Venue not found: " + unknownId));
    }

    @Test
    void put_malformedId_returnsTheExisting400Behavior() throws Exception {
        mockMvc.perform(put("/api/admin/venues/not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(venueService, never()).update(any(), any(), any(), any());
    }

    // ==================== DELETE /api/admin/venues/{id} ====================

    @Test
    void deleteValidId_reachesVenueServiceDelete_andReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(venueService).delete(id);

        mockMvc.perform(delete("/api/admin/venues/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRequest_forwardsTheCorrectIdToVenueService() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/venues/{id}", id))
                .andExpect(status().isNoContent());

        verify(venueService).delete(id);
    }

    @Test
    void delete_unknownId_returns404_withTheExistingEntityNotFoundBehavior() throws Exception {
        UUID unknownId = UUID.randomUUID();
        doThrow(new CatalogEntityNotFoundException("Venue", unknownId)).when(venueService).delete(unknownId);

        mockMvc.perform(delete("/api/admin/venues/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Venue not found: " + unknownId));
    }

    @Test
    void delete_fkConflict_returns409_withTheExistingDataIntegrityConflictBehavior() throws Exception {
        // A Venue still referenced by a Seat or Show (fk_seats_venue /
        // fk_shows_venue, both ON DELETE RESTRICT) — not pre-checked by
        // VenueService.delete, left to the existing
        // DataIntegrityViolationException handler.
        UUID id = UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("fk_shows_venue")).when(venueService).delete(id);

        mockMvc.perform(delete("/api/admin/venues/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DATA_INTEGRITY_CONFLICT"));
    }

    @Test
    void delete_malformedId_returnsTheExisting400Behavior() throws Exception {
        mockMvc.perform(delete("/api/admin/venues/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(venueService, never()).delete(any());
    }
}
