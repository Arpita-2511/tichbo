package com.eventtick.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints JWTs shaped like the ones user-service issues, for gateway tests.
 * Everything here is a made-up, test-only value: the secrets below are not
 * used by any real environment, and tests never print the tokens.
 */
final class TestTokens {

    /**
     * What the gateway under test is configured with (see the test's properties).
     * 64+ bytes on purpose: the signer only allows HS512 for a key that long,
     * and the tests mint all three HMAC variants.
     */
    static final String SECRET =
            "gateway-test-only-secret-not-used-anywhere-else-0123456789-abcdefghijklmnop";
    static final String ISSUER = "test-issuer";
    static final String AUDIENCE = "test-audience";

    /** A different secret of valid length, for "signed by someone else" tokens. */
    static final String OTHER_SECRET = "a-completely-different-test-only-secret-0987654321";

    private TestTokens() {
    }

    /** Starts from a fully valid HS256 token; override one thing per test. */
    static Builder valid() {
        return new Builder();
    }

    static final class Builder {
        private String secret = SECRET;
        private JWSAlgorithm algorithm = JWSAlgorithm.HS256;
        private String issuer = ISSUER;
        private String audience = AUDIENCE;
        private String subject = UUID.randomUUID().toString();
        private String role = "CUSTOMER";
        private String plan = "FREE";
        private Instant expiresAt = Instant.now().plus(Duration.ofMinutes(10));

        Builder secret(String secret) {
            this.secret = secret;
            return this;
        }

        Builder algorithm(JWSAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        Builder role(String role) {
            this.role = role;
            return this;
        }

        Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /** No {@code exp} claim at all. */
        Builder neverExpires() {
            this.expiresAt = null;
            return this;
        }

        private JWTClaimsSet claims() {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .audience(audience)
                    .claim("email", "test@example.com")
                    .claim("role", role)
                    .claim("plan", plan)
                    .issueTime(Date.from(Instant.now().minusSeconds(1)));
            if (expiresAt != null) {
                claims.expirationTime(Date.from(expiresAt));
            }
            return claims.build();
        }

        String build() {
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims());
                jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException("could not mint test token", e);
            }
        }

        /** Unsigned ({@code alg: none}) token carrying otherwise valid claims. */
        String buildUnsigned() {
            return new PlainJWT(claims()).serialize();
        }
    }
}
