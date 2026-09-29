package com.eventtick.booking;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints JWTs the same way user-service does (HS256, same issuer/audience, `sub`
 * = user id, `role` claim), signed with the secret in
 * {@code src/test/resources/application.yml}. Mirrors payment-service's own
 * {@code TestTokens}.
 */
public final class TestTokens {

    public static final String SECRET = "test-only-secret-for-the-spring-context-must-be-at-least-32-bytes";
    public static final String ISSUER = "eventtick-user-service";
    public static final String AUDIENCE = "eventtick-clients";

    private TestTokens() {
    }

    public static Builder valid() {
        return new Builder();
    }

    /** {@code Authorization} header value for a customer with this id. */
    public static String customer(UUID userId) {
        return "Bearer " + valid().subject(userId).role("CUSTOMER").build();
    }

    /** {@code Authorization} header value for an administrator with this id. */
    public static String admin(UUID userId) {
        return "Bearer " + valid().subject(userId).role("ADMIN").build();
    }

    public static final class Builder {
        private String subject = UUID.randomUUID().toString();
        private String role = "CUSTOMER";
        private Instant expiresAt = Instant.now().plus(Duration.ofMinutes(10));

        public Builder subject(UUID subject) {
            this.subject = subject.toString();
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public String build() {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Instant now = Instant.now();
            return Jwts.builder()
                    .subject(subject)
                    .claim("role", role)
                    .issuer(ISSUER)
                    .audience().add(AUDIENCE).and()
                    .issuedAt(Date.from(now.minusSeconds(1)))
                    .expiration(Date.from(expiresAt))
                    .signWith(key)
                    .compact();
        }
    }
}
