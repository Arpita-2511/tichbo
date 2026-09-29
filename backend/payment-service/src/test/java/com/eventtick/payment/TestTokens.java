package com.eventtick.payment;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints JWTs shaped like the ones user-service issues, signed with the same
 * secret payment-service's test {@code application.yml} configures — for
 * driving real security-filter-chain tests. Everything here is a made-up,
 * test-only value; tests never print the tokens.
 */
public final class TestTokens {

    /** Must match src/test/resources/application.yml's jwt.* exactly. */
    public static final String SECRET = "test-only-secret-for-the-spring-context-must-be-at-least-32-bytes";
    public static final String ISSUER = "eventtick-user-service";
    public static final String AUDIENCE = "eventtick-clients";

    private TestTokens() {
    }

    public static Builder valid() {
        return new Builder();
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
