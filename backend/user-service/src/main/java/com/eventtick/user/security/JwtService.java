package com.eventtick.user.security;

import com.eventtick.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates the JWTs used for authentication (see
 * {@code docs/architecture.md} §23). Self-issued/self-validated — this
 * service is its own signing authority, there is no external
 * authorization server.
 *
 * <p>Claims: {@code sub} (user id), {@code email}, {@code role},
 * {@code plan} (plan name), {@code iat}, {@code exp}, {@code iss},
 * {@code aud}. No {@code nbf} — not needed for this simple case. No
 * sensitive data (password hash, etc.) is ever placed in a claim.
 *
 * <p>Signed with HMAC-SHA256; the signing key comes from {@code
 * jwt.secret} (see {@code application.yml}), which must be at least 32
 * bytes — a shorter key would make JJWT throw a
 * {@code WeakKeyException} at startup.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final String audience;
    private final Duration expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience,
            @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    /** Seconds until a freshly generated token expires — for {@code AuthResponse.expiresInSeconds}. */
    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("plan", user.getPlan().getName())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates signature, issuer, audience, and expiration (all enforced
     * by the parser itself), and returns the claims if valid.
     *
     * @return empty if the token is missing, malformed, expired, has an
     *         invalid signature, or fails the issuer/audience check —
     *         deliberately not distinguishing which, so callers (and
     *         eventually clients) can't probe why a token was rejected.
     */
    public Optional<JwtAuthentication> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new JwtAuthentication(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    com.eventtick.user.entity.UserRole.valueOf(claims.get("role", String.class)),
                    claims.get("plan", String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            // JwtException covers expired/malformed/bad-signature/claim-mismatch;
            // IllegalArgumentException covers a sub that isn't a valid UUID or a
            // role that isn't a known UserRole. Either way: not a valid token.
            return Optional.empty();
        }
    }
}
