package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ContentRequest;
import com.eventtick.catalog.dto.ContentResponse;
import com.eventtick.catalog.entity.Content;
import com.eventtick.catalog.service.ContentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST surface for {@link Content}. Delegates entirely to
 * {@link ContentService} — no persistence or validation logic here.
 * Exception-to-HTTP-status mapping lives in
 * {@code com.eventtick.catalog.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/catalog/content")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @PostMapping
    public ResponseEntity<ContentResponse> create(@Valid @RequestBody ContentRequest request) {
        Content content = contentService.create(request.type(), request.title(), request.description(),
                request.language(), request.duration(), request.genre(), request.releaseOrEventDate());
        return ResponseEntity.created(URI.create("/api/catalog/content/" + content.getId()))
                .body(ContentResponse.from(content));
    }

    @GetMapping
    public List<ContentResponse> list() {
        return contentService.list().stream().map(ContentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ContentResponse getById(@PathVariable UUID id) {
        return ContentResponse.from(contentService.getById(id));
    }

    @PutMapping("/{id}")
    public ContentResponse update(@PathVariable UUID id, @Valid @RequestBody ContentRequest request) {
        Content content = contentService.update(id, request.type(), request.title(), request.description(),
                request.language(), request.duration(), request.genre(), request.releaseOrEventDate());
        return ContentResponse.from(content);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        contentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
