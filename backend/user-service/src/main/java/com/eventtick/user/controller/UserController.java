package com.eventtick.user.controller;

import com.eventtick.user.dto.UserResponse;
import com.eventtick.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Requires authentication (see {@code SecurityConfig}: everything outside
 * {@code /api/auth/**} does) — {@link #me} for any authenticated user,
 * {@link #listUsers} additionally requires {@code role=ADMIN}, enforced at
 * the gateway ({@code /api/admin/** -> ROLE_ADMIN}, Phase 13.3), not here.
 *
 * <p>No class-level {@code @RequestMapping}: {@link #me} lives under
 * {@code /api/users} and {@link #listUsers} under {@code /api/admin/users},
 * two different top-level prefixes Spring cannot combine with one shared
 * class-level base path, so each method states its full path.
 *
 * <p>{@link #me} derives identity from the {@link Authentication}
 * Spring Security injects (its principal is the user id, set by
 * {@code JwtAuthenticationFilter}) — never from a path or query
 * parameter, exactly per the project's requirement that a client cannot
 * view another user's data by changing an id in the request.
 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users/me")
    public UserResponse me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return UserResponse.from(userService.getById(userId));
    }

    /**
     * {@code GET /api/admin/users} (Phase 13.4) — the first admin operation.
     * Standard {@code page}/{@code size}/{@code sort} query parameters via
     * {@link Pageable}; newest users first by default. No search/filtering
     * yet, and no single-user admin lookup yet (only this list).
     */
    @GetMapping("/api/admin/users")
    public Page<UserResponse> listUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.listAll(pageable).map(UserResponse::from);
    }
}
