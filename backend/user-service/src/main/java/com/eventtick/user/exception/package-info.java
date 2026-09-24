/**
 * Custom exceptions — {@code DuplicateEmailException},
 * {@code InvalidCredentialsException}, {@code UserNotFoundException} —
 * and {@link com.eventtick.user.exception.GlobalExceptionHandler}, which
 * maps them (and standard Spring/bean-validation failures) to HTTP
 * responses for {@code com.eventtick.user.controller}.
 */
package com.eventtick.user.exception;
