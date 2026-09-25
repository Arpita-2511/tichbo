package com.eventtick.user.controller;

import com.eventtick.user.dto.ChangePlanRequest;
import com.eventtick.user.dto.ChangeRoleRequest;
import com.eventtick.user.dto.UserResponse;
import com.eventtick.user.entity.User;
import com.eventtick.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Phase 13, FR-37: the remaining admin User Management write operations. A
 * separate controller from {@link UserController} — not new methods added
 * there — following the same convention every other admin operation in
 * this project uses (a dedicated, minimal admin controller reusing
 * existing service methods/DTOs).
 *
 * <p><b>Authorization:</b> {@code role=ADMIN} is enforced at the API
 * Gateway ({@code /api/admin/** -> hasAuthority("ROLE_ADMIN")}, Phase
 * 13.3) — this class performs no role check of its own, the same boundary
 * every other admin-only path in this project relies on.
 *
 * <p><b>Self-role protection ({@link #changeRole}):</b> the acting
 * administrator's own id comes from {@link Authentication}, populated by
 * {@code JwtAuthenticationFilter} from the caller's own validated JWT —
 * exactly the same mechanism {@link UserController#me} already uses, and
 * independent of the Gateway (user-service validates the forwarded
 * {@code Authorization} header itself, defense in depth). It is never
 * read from the request body or the {@code userId} path variable, so a
 * forged body cannot bypass the self-role check in
 * {@code UserService.changeRole}.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/{userId}/plan")
    public UserResponse changePlan(@PathVariable UUID userId, @Valid @RequestBody ChangePlanRequest request) {
        User user = userService.changePlan(userId, request.planId());
        return UserResponse.from(user);
    }

    @PatchMapping("/{userId}/role")
    public UserResponse changeRole(@PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest request,
                                    Authentication authentication) {
        UUID requestingAdminId = UUID.fromString(authentication.getName());
        User user = userService.changeRole(userId, request.role(), requestingAdminId);
        return UserResponse.from(user);
    }
}
