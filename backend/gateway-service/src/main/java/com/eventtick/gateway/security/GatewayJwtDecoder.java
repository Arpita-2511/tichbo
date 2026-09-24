package com.eventtick.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Validates the JWTs issued by user-service. It must accept exactly what
 * {@code user-service}'s {@code JwtService} accepts (and nothing looser):
 *
 * <ul>
 *   <li><b>Signature:</b> HMAC with the shared secret, taken as the raw
 *   UTF-8 bytes of {@code jwt.secret} — <i>not</i> base64-decoded, exactly
 *   like {@code Keys.hmacShaKeyFor(secret.getBytes(UTF_8))} in user-service.
 *   <li><b>Algorithm:</b> user-service calls {@code signWith(key)} without
 *   naming an algorithm, so JJWT picks the strongest HMAC one the key length
 *   allows: HS256 for a 32–47 byte secret, HS384 for 48–63, HS512 for 64+.
 *   So the algorithm depends on the secret's length, and this accepts all
 *   three. Each is pinned to its own decoder; anything else ({@code none},
 *   RSA/EC, ...) is rejected before any verification is attempted.
 *   <li><b>Claims:</b> {@code exp} must be present and not passed (zero clock
 *   skew — Spring's default 60s leeway would accept tokens user-service
 *   already rejects), {@code iss} and {@code aud} must match the configured
 *   values, {@code sub} must be a UUID and {@code role} a known role — the
 *   same structural checks user-service's {@code parseToken} performs.
 * </ul>
 *
 * Every failure surfaces as a generic {@link BadJwtException}; which check
 * failed is deliberately not exposed to callers.
 */
public final class GatewayJwtDecoder {

    /** Mirrors user-service's {@code UserRole}; a token with any other role is invalid. */
    static final Set<String> KNOWN_ROLES = Set.of("CUSTOMER", "ADMIN");

    private static final int MIN_SECRET_BYTES = 32;

    private GatewayJwtDecoder() {
    }

    public static ReactiveJwtDecoder create(String secret, String issuer, String audience) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            // user-service refuses to start with a shorter secret too (JJWT's
            // WeakKeyException); failing here keeps the two consistent.
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (set JWT_SECRET to the same value user-service uses).");
        }

        OAuth2TokenValidator<Jwt> validator = validator(issuer, audience);
        Map<JWSAlgorithm, ReactiveJwtDecoder> byAlgorithm = Map.of(
                JWSAlgorithm.HS256, pinned(keyBytes, "HmacSHA256", MacAlgorithm.HS256, validator),
                JWSAlgorithm.HS384, pinned(keyBytes, "HmacSHA384", MacAlgorithm.HS384, validator),
                JWSAlgorithm.HS512, pinned(keyBytes, "HmacSHA512", MacAlgorithm.HS512, validator));
        return new AlgorithmDispatchingDecoder(byAlgorithm);
    }

    private static ReactiveJwtDecoder pinned(byte[] keyBytes, String keyAlgorithm, MacAlgorithm alg,
                                             OAuth2TokenValidator<Jwt> validator) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withSecretKey(new SecretKeySpec(keyBytes, keyAlgorithm))
                .macAlgorithm(alg)
                .build();
        decoder.setJwtValidator(validator);
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtClaimValidator<Instant>("exp", Objects::nonNull),
                new JwtIssuerValidator(issuer),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>("sub", GatewayJwtDecoder::isUuid),
                new JwtClaimValidator<Object>("role", role -> role instanceof String r && KNOWN_ROLES.contains(r)));
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    /** Reads only the (unverified) header to choose which pinned decoder handles the token. */
    private record AlgorithmDispatchingDecoder(Map<JWSAlgorithm, ReactiveJwtDecoder> byAlgorithm)
            implements ReactiveJwtDecoder {

        @Override
        public Mono<Jwt> decode(String token) {
            JWT parsed;
            try {
                parsed = JWTParser.parse(token);
            } catch (ParseException e) {
                return Mono.error(new BadJwtException("Invalid token"));
            }
            ReactiveJwtDecoder decoder = byAlgorithm.get(parsed.getHeader().getAlgorithm());
            if (decoder == null) {
                return Mono.error(new BadJwtException("Invalid token"));
            }
            return decoder.decode(token);
        }
    }
}
