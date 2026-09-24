package com.eventtick.user.controller;

import com.eventtick.user.dto.UserResponse;
import com.eventtick.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code /api/users/**} — requires authentication (see
 * {@code SecurityConfig}: everything outside {@code /api/auth/**} does).
 *
 * <p>{@link #me} derives identity from the {@link Authentication}
 * Spring Security injects (its principal is the user id, set by
 * {@code JwtAuthenticationFilter}) — never from a path or query
 * parameter, exactly per the project's requirement that a client cannot
 * view another user's data by changing an id in the request.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return UserResponse.from(userService.getById(userId));
    }
}
