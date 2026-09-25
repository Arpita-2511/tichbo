package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ContentRequest;
import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.entity.ContentType;
import com.eventtick.catalog.service.ContentService;
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
 * Phase 13.5.2: proves the five existing {@code /api/catalog/content}
 * routes still resolve and behave exactly as before, now that
 * {@link AdminContentController} exists alongside {@link ContentController}
 * (a separate class specifically so the two controllers' route tables
 * can't collide — see {@link AdminContentController}'s Javadoc). No test
 * previously existed for {@link ContentController} at all — this is new
 * coverage, not a modification of an existing test.
 */
@WebMvcTest(ContentController.class)
class ContentControllerRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContentService contentService;

    private static Content sampleContent() {
        Content content = new Content();
        ReflectionTestUtils.setField(content, "id", UUID.randomUUID());
        content.setType(ContentType.MOVIE);
        content.setTitle("Inception");
        ReflectionTestUtils.setField(content, "createdAt", Instant.parse("2026-09-25T10:00:00Z"));
        ReflectionTestUtils.setField(content, "updatedAt", Instant.parse("2026-09-25T10:00:00Z"));
        return content;
    }

    @Test
    void postApiCatalogContent_stillCreates() throws Exception {
        Content saved = sampleContent();
        when(contentService.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(saved);
        ContentRequest request = new ContentRequest(ContentType.MOVIE, "Inception", null, null, null, null, null);

        mockMvc.perform(post("/api/catalog/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/catalog/content/" + saved.getId()));
    }

    @Test
    void getApiCatalogContent_stillLists() throws Exception {
        when(contentService.list()).thenReturn(List.of(sampleContent()));

        mockMvc.perform(get("/api/catalog/content"))
                .andExpect(status().isOk());
    }

    @Test
    void getApiCatalogContentById_stillReturnsOne() throws Exception {
        Content content = sampleContent();
        when(contentService.getById(content.getId())).thenReturn(content);

        mockMvc.perform(get("/api/catalog/content/" + content.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void putApiCatalogContentById_stillUpdates() throws Exception {
        Content updated = sampleContent();
        when(contentService.update(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(updated);
        ContentRequest request = new ContentRequest(ContentType.MOVIE, "Inception (Updated)", null, null, null, null, null);

        mockMvc.perform(put("/api/catalog/content/" + updated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteApiCatalogContentById_stillDeletes() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/catalog/content/" + id))
                .andExpect(status().isNoContent());
    }
}
