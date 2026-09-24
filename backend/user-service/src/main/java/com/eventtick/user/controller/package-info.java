/**
 * REST controllers exposing the User Service's public API:
 * {@link com.eventtick.user.controller.AuthController}
 * ({@code /api/auth/register}, {@code /api/auth/login} — public) and
 * {@link com.eventtick.user.controller.UserController}
 * ({@code /api/users/me} — authenticated). Delegates entirely to
 * {@code com.eventtick.user.service}; exception-to-HTTP-status mapping
 * lives in {@code com.eventtick.user.exception.GlobalExceptionHandler}
 * (and, for authentication failures specifically, in
 * {@code com.eventtick.user.config.SecurityConfig}'s entry point).
 *
 * <p>No subscription-plan-change or admin user-management endpoints yet —
 * out of scope for the authentication phase.
 */
package com.eventtick.user.controller;
