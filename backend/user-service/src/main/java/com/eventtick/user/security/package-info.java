/**
 * JWT issuance and validation:
 * {@link com.eventtick.user.security.JwtService} and
 * {@link com.eventtick.user.security.JwtAuthenticationFilter}.
 *
 * <p>A new package, distinct from {@code com.eventtick.user.config}
 * (which holds {@code @Configuration} beans) — this holds the actual
 * token/filter logic, which didn't fit the pre-existing
 * controller/service/repository/entity/dto/config/exception skeleton
 * created before authentication was implemented.
 */
package com.eventtick.user.security;
