package com.eventtick.user.service;

import com.eventtick.user.entity.User;

/**
 * Internal result of a successful login — never returned directly over
 * the API. {@code AuthController} maps this to {@code AuthResponse}.
 */
public record AuthResult(User user, String accessToken, long expiresInSeconds) {
}
