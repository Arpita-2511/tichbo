package com.eventtick.booking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates the JWTs user-service issues (same secret, issuer and audience;
 * `sub` = user id, `role` = CUSTOMER or ADMIN). A deliberate mirror of
 * payment-service's {@code JwtService} rather than a shared library — this
 * project has no shared-module convention, and each service already owns its
 * own copy of this small class.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
    }

    /** Empty for any token that is expired, malformed, badly signed, or carries an unusable claim. */
    public Optional<CallerIdentity> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String role = claims.get("role", String.class);
            if (!"CUSTOMER".equals(role) && !"ADMIN".equals(role)) {
                return Optional.empty();
            }
            return Optional.of(new CallerIdentity(UUID.fromString(claims.getSubject()), "ADMIN".equals(role)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
