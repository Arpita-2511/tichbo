/**
 * Business logic for user registration, authentication, and profile
 * lookups: {@link com.eventtick.user.service.AuthService} (register,
 * login, password hashing, JWT issuance) and
 * {@link com.eventtick.user.service.UserService} (read-only lookups, used
 * by {@code GET /api/users/me}). {@link com.eventtick.user.service.AuthResult}
 * is an internal login result, never returned directly over the API.
 *
 * <p>Subscription-plan *changes* after registration are not implemented
 * yet — only the default-plan assignment at registration.
 */
package com.eventtick.user.service;
