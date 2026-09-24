package com.eventtick.user.exception;

/**
 * Thrown on login failure. Maps to 401.
 *
 * <p>Used for both "no user with this email" and "password doesn't
 * match" — deliberately the same exception with the same message either
 * way. Per the project's own security requirement: never reveal whether
 * an email exists or how close a wrong password was.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
