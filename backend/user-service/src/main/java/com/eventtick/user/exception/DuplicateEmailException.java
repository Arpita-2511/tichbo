package com.eventtick.user.exception;

/** Thrown at registration when the (lowercased) email is already in use. Maps to 409. */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("An account with this email already exists.");
        // Deliberately does not include the email in the message — no
        // functional reason to echo it back, and keeping the message
        // generic costs nothing.
    }
}
