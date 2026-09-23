/**
 * Request/response DTOs for the User Service's public API, kept separate
 * from JPA entities so the API contract can evolve independently of the
 * persistence model (and so password hashes/internal fields are never
 * accidentally serialized).
 *
 * <p>Not implemented yet.
 */
package com.eventtick.user.dto;
