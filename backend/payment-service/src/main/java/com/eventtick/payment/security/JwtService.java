package com.eventtick.payment.security;

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
 * Validates the JWTs user-service issues — this service never issues a
 * token itself, only validates one, so unlike user-service's own
 * {@code JwtService} there is no {@code generateToken}. Same signing key/
 * issuer/audience as user-service and gateway-service (see
 * {@code application.yml}'s {@code jwt.*}) — this is defense-in-depth
 * validation, the same relationship user-service's own {@code /api/users/me}
 * already has with the Gateway's independent validation.
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

    /**
     * Validates signature, issuer, audience, and expiration (all enforced
     * by the parser itself), and returns the claims if valid.
     *
     * @return empty if the token is missing, malformed, expired, has an
     *         invalid signature, or fails the issuer/audience check —
     *         deliberately not distinguishing which, matching user-service's
     *         own {@code JwtService.parseToken}.
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
                    PaymentRole.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            // JwtException covers expired/malformed/bad-signature/claim-mismatch;
            // IllegalArgumentException covers a sub that isn't a valid UUID or a
            // role that isn't CUSTOMER/ADMIN. Either way: not a valid token.
            return Optional.empty();
        }
    }
}
