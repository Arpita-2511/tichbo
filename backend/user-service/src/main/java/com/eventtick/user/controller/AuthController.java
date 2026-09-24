package com.eventtick.user.controller;

import com.eventtick.user.dto.AuthResponse;
import com.eventtick.user.dto.LoginRequest;
import com.eventtick.user.dto.RegisterRequest;
import com.eventtick.user.dto.UserResponse;
import com.eventtick.user.entity.User;
import com.eventtick.user.service.AuthResult;
import com.eventtick.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints — {@code /api/auth/**} is
 * {@code permitAll()} in {@code SecurityConfig}. Delegates entirely to
 * {@link AuthService}; no password/token logic here.
 *
 * <p>{@link #register} returns {@code 201} with just the created
 * {@link UserResponse} (no {@code Location} header — there is no
 * {@code GET /api/users/{id}} endpoint in this phase to point at, only
 * {@code /api/users/me}; adding one wasn't requested). {@link #login}
 * returns {@code 200} with a full {@link AuthResponse} (token + user) —
 * registration deliberately does not also issue a token, keeping
 * "create the resource" and "authenticate" as separate concerns.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.email(), request.password());
        return AuthResponse.of(result.accessToken(), result.expiresInSeconds(), UserResponse.from(result.user()));
    }
}
