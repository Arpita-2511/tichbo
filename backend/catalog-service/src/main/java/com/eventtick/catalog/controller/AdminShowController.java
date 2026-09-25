package com.eventtick.catalog.controller;

import com.eventtick.catalog.dto.ShowResponse;
import com.eventtick.catalog.entity.Show;
import com.eventtick.catalog.service.ShowService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phase 13.6.2: the first admin Show operation. A separate, minimal
 * controller — not a method added to {@link ShowController} — for the same
 * reason {@link AdminContentController} is separate from
 * {@link com.eventtick.catalog.controller.ContentController}:
 * {@code @RequestMapping} paths are always concatenated (class-level +
 * method-level), so hosting both {@code /api/catalog/shows} and
 * {@code /api/admin/shows} in one class would require restructuring its
 * existing mappings. A second controller avoids that risk entirely —
 * {@link ShowController} and its five existing routes are not touched.
 *
 * <p>Delegates to {@link ShowService#cancel}, a small new method added
 * specifically for this endpoint (unlike {@link AdminContentController},
 * which reuses an existing method unchanged): {@link ShowService#update}
 * is a full replace of every mutable field, which does not fit "cancel and
 * touch nothing else" — see {@link ShowService#cancel}'s own Javadoc.
 *
 * <p><b>Authorization:</b> enforced entirely at the API Gateway
 * ({@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}, Phase 13.3) — this
 * class performs no role check itself; catalog-service has no Spring
 * Security dependency, and none is added here. The same pre-existing,
 * disclosed condition as {@link AdminContentController}: called directly
 * against catalog-service (bypassing the Gateway), this endpoint accepts
 * any request the same way the existing {@code PUT /api/catalog/shows/{id}}
 * already does.
 */
@RestController
@RequestMapping("/api/admin/shows")
public class AdminShowController {

    private final ShowService showService;

    public AdminShowController(ShowService showService) {
        this.showService = showService;
    }

    @PatchMapping("/{id}/cancel")
    public ShowResponse cancel(@PathVariable UUID id) {
        Show show = showService.cancel(id);
        return ShowResponse.from(show);
    }
}
