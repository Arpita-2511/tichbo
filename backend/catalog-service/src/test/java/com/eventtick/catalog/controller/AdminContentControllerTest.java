package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ContentRequest;
import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.ContentType;
import com.eventtick.catalog.exception.CatalogEntityNotFoundException;
import com.eventtick.catalog.service.ContentService;
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
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.5.2 ({@code POST}) / FR-38 ({@code PUT}/{@code DELETE}):
 * {@code /api/admin/content}. A {@code @WebMvcTest} slice — real
 * {@link org.springframework.web.bind.annotation.RestControllerAdvice}
 * (so bean-validation/error-mapping behavior is the genuine, unmodified
 * {@code GlobalExceptionHandler}), real Jakarta Bean Validation, but a
 * mocked {@link ContentService} — catalog-service has no schema-generation
 * mechanism for its H2 test database ({@code ddl-auto: none} even for
 * tests, unlike user-service; see the Phase 13.5 inspection report), so a
 * real end-to-end repository write isn't available to this test suite.
 * Nothing about these endpoints' own logic needs one anyway: they delegate
 * entirely to the existing, already-implemented
 * {@code ContentService.create}/{@code update}/{@code delete}.
 *
 * <p>Complements {@link ContentControllerRegressionTest}, which proves the
 * five <i>existing</i> {@code /api/catalog/content} routes are unaffected
 * by this controller.
 */
@WebMvcTest(AdminContentController.class)
class AdminContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContentService contentService;

    private static ContentRequest validRequest() {
        return new ContentRequest(ContentType.MOVIE, "Inception", "A mind-bending heist.", "English",
                148, "Sci-Fi", LocalDate.of(2010, 7, 16));
    }

    private static Content savedContent(ContentRequest request) {
        Content content = new Content();
        ReflectionTestUtils.setField(content, "id", UUID.randomUUID());
        content.setType(request.type());
        content.setTitle(request.title());
        content.setDescription(request.description());
        content.setLanguage(request.language());
        content.setDuration(request.duration());
        content.setGenre(request.genre());
        content.setReleaseOrEventDate(request.releaseOrEventDate());
        ReflectionTestUtils.setField(content, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(content, "updatedAt", Instant.parse("2026-09-25T10:00:00Z"));
        return content;
    }

    // ---- A: a valid request reaches ContentService and returns 201 ----

    @Test
    void validRequest_reachesContentService_andReturns201() throws Exception {
        ContentRequest request = validRequest();
        Content saved = savedContent(request);
        when(contentService.create(request.type(), request.title(), request.description(), request.language(),
                request.duration(), request.genre(), request.releaseOrEventDate())).thenReturn(saved);

        mockMvc.perform(post("/api/admin/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/catalog/content/" + saved.getId()))
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.title").value("Inception"));

        // Exactly the existing ContentService.create — no new/duplicated logic.
        verify(contentService).create(request.type(), request.title(), request.description(), request.language(),
                request.duration(), request.genre(), request.releaseOrEventDate());
    }

    // ---- B: the response is exactly ContentResponse, nothing more/less ----

    @Test
    void response_isExactlyTheContentResponseShape() throws Exception {
        ContentRequest request = validRequest();
        Content saved = savedContent(request);
        when(contentService.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(saved);

        String body = mockMvc.perform(post("/api/admin/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        Set<String> fieldNames = new java.util.LinkedHashSet<>();
        for (Iterator<String> it = json.fieldNames(); it.hasNext(); ) {
            fieldNames.add(it.next());
        }
        // The exact ContentResponse record components — no admin-specific
        // extra field, no accidental leak of anything else.
        assertThat(fieldNames).containsExactlyInAnyOrder(
                "id", "type", "title", "description", "language", "duration", "genre",
                "releaseOrEventDate", "createdAt", "updatedAt");
    }

    // ---- C: an invalid request gets the existing validation error format ----

    @Test
    void blankTitle_returns400_withTheExistingValidationErrorFormat() throws Exception {
        ContentRequest invalid = new ContentRequest(ContentType.MOVIE, "", null, null, null, null, null);

        mockMvc.perform(post("/api/admin/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(contentService, org.mockito.Mockito.never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingType_returns400() throws Exception {
        // type is @NotNull on ContentRequest — omit it entirely.
        String jsonWithoutType = "{\"title\":\"Some Title\"}";

        mockMvc.perform(post("/api/admin/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithoutType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void nonPositiveDuration_returns400() throws Exception {
        ContentRequest invalid = new ContentRequest(ContentType.MOVIE, "Valid Title", null, null, 0, null, null);

        mockMvc.perform(post("/api/admin/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ==================== PUT /api/admin/content/{id} ====================

    @Test
    void putValidRequest_reachesContentServiceUpdate_andReturns200WithContentResponse() throws Exception {
        ContentRequest request = validRequest();
        Content updated = savedContent(request);
        UUID id = updated.getId();
        when(contentService.update(eq(id), eq(request.type()), eq(request.title()), eq(request.description()),
                eq(request.language()), eq(request.duration()), eq(request.genre()),
                eq(request.releaseOrEventDate()))).thenReturn(updated);

        mockMvc.perform(put("/api/admin/content/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Inception"));
    }

    @Test
    void putRequest_forwardsTheCorrectIdToContentService() throws Exception {
        ContentRequest request = validRequest();
        UUID id = UUID.randomUUID();
        when(contentService.update(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(savedContent(request));

        mockMvc.perform(put("/api/admin/content/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(contentService).update(id, request.type(), request.title(), request.description(),
                request.language(), request.duration(), request.genre(), request.releaseOrEventDate());
    }

    @Test
    void put_blankTitle_returns400_withTheExistingValidationErrorFormat_andNeverReachesTheService() throws Exception {
        ContentRequest invalid = new ContentRequest(ContentType.MOVIE, "", null, null, null, null, null);

        mockMvc.perform(put("/api/admin/content/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(contentService, org.mockito.Mockito.never())
                .update(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void put_unknownId_returns404_withTheExistingEntityNotFoundBehavior() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(contentService.update(eq(unknownId), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new CatalogEntityNotFoundException("Content", unknownId));

        mockMvc.perform(put("/api/admin/content/{id}", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"));
    }

    // ==================== DELETE /api/admin/content/{id} ====================

    @Test
    void deleteValidId_reachesContentServiceDelete_andReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(contentService).delete(id);

        mockMvc.perform(delete("/api/admin/content/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRequest_forwardsTheCorrectIdToContentService() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/content/{id}", id))
                .andExpect(status().isNoContent());

        verify(contentService).delete(id);
    }

    @Test
    void delete_unknownId_returns404_withTheExistingEntityNotFoundBehavior() throws Exception {
        UUID unknownId = UUID.randomUUID();
        doThrow(new CatalogEntityNotFoundException("Content", unknownId)).when(contentService).delete(unknownId);

        mockMvc.perform(delete("/api/admin/content/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void delete_fkConflict_returns409_withTheExistingDataIntegrityConflictBehavior() throws Exception {
        // A Content row still referenced by a Show (fk_shows_content ON
        // DELETE RESTRICT) — not pre-checked by ContentService.delete, left
        // to the existing DataIntegrityViolationException handler.
        UUID id = UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("fk_shows_content")).when(contentService).delete(id);

        mockMvc.perform(delete("/api/admin/content/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DATA_INTEGRITY_CONFLICT"));
    }
}
