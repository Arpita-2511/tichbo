package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ShowCreateRequest;
import com.eventtick.catalog.dto.ShowResponse;
import com.eventtick.catalog.dto.ShowUpdateRequest;
import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.service.ShowService;
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
 * REST surface for {@link Show}. Delegates entirely to
 * {@link ShowService} — no persistence or validation logic here.
 *
 * <p>{@link #create} uses {@link ShowCreateRequest}, which has no
 * {@code status} field — {@code ShowService.create} always sets a new
 * show to {@code SCHEDULED} itself, so there is nothing for this
 * controller to pass through even if it wanted to. {@link #update} uses
 * {@link ShowUpdateRequest}, which does carry {@code status}.
 *
 * <p>{@link #list} does not filter by {@code contentId}/{@code venueId}
 * — {@code ShowService.listByContent}/{@code listByVenue} already exist
 * but weren't wired here, since filtering was only requested for
 * {@code SeatController}.
 */
@RestController
@RequestMapping("/api/catalog/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody ShowCreateRequest request) {
        Show show = showService.create(request.contentId(), request.venueId(),
                request.startTime(), request.endTime());
        return ResponseEntity.created(URI.create("/api/catalog/shows/" + show.getId()))
                .body(ShowResponse.from(show));
    }

    @GetMapping
    public List<ShowResponse> list() {
        return showService.list().stream().map(ShowResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ShowResponse getById(@PathVariable UUID id) {
        return ShowResponse.from(showService.getById(id));
    }

    @PutMapping("/{id}")
    public ShowResponse update(@PathVariable UUID id, @Valid @RequestBody ShowUpdateRequest request) {
        Show show = showService.update(id, request.contentId(), request.venueId(),
                request.startTime(), request.endTime(), request.status());
        return ShowResponse.from(show);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        showService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
