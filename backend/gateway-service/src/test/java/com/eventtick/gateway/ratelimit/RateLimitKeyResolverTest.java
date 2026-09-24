package com.eventtick.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyResolverTest {

    private final RateLimitKeyResolver resolver = new RateLimitKeyResolver();

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("test-token-value")
                .header("alg", "HS256")
                .subject(subject)
                .claim("role", "CUSTOMER")
                .issuedAt(Instant.now().minusSeconds(1))
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private MockServerWebExchange exchangeFrom(String remoteAddress) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/users/me");
        if (remoteAddress != null) {
            builder.remoteAddress(new InetSocketAddress(remoteAddress, 12345));
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void authenticatedRequest_isKeyedByTheJwtSubject() {
        String subject = UUID.randomUUID().toString();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt(subject),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")), subject);

        String key = resolver.resolve(exchangeFrom("203.0.113.5"))
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        reactor.core.publisher.Mono.just(new SecurityContextImpl(auth))))
                .block();

        assertThat(key).isEqualTo("user:" + subject);
    }

    @Test
    void twoDifferentSubjects_produceTwoDifferentKeys() {
        String key1 = resolver.resolve(exchangeFrom("203.0.113.5"))
                .contextWrite(withJwtSubject("11111111-1111-1111-1111-111111111111"))
                .block();
        String key2 = resolver.resolve(exchangeFrom("203.0.113.5"))
                .contextWrite(withJwtSubject("22222222-2222-2222-2222-222222222222"))
                .block();

        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1).isEqualTo("user:11111111-1111-1111-1111-111111111111");
        assertThat(key2).isEqualTo("user:22222222-2222-2222-2222-222222222222");
    }

    @Test
    void noAuthenticationAtAll_fallsBackToTheClientAddress() {
        String key = resolver.resolve(exchangeFrom("198.51.100.7")).block();

        assertThat(key).isEqualTo("ip:198.51.100.7");
    }

    @Test
    void anonymousAuthenticationToken_isTreatedAsUnauthenticated_notAsAUser() {
        // Spring Security's default AnonymousAuthenticationToken reports
        // isAuthenticated() == true; this must not be mistaken for a real JWT.
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken("key", "anonymousUser", authorities);

        String key = resolver.resolve(exchangeFrom("198.51.100.9"))
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        reactor.core.publisher.Mono.just(new SecurityContextImpl(anonymous))))
                .block();

        assertThat(key).isEqualTo("ip:198.51.100.9");
    }

    @Test
    void aClientSuppliedForwardedHeader_isIgnored() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me")
                        .header("X-Forwarded-For", "1.2.3.4")
                        .remoteAddress(new InetSocketAddress("198.51.100.20", 12345))
                        .build());

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("ip:198.51.100.20").isNotEqualTo("ip:1.2.3.4");
    }

    @Test
    void aMissingRemoteAddress_fallsBackToASharedUnknownBucket_ratherThanFailing() {
        String key = resolver.resolve(exchangeFrom(null)).block();

        assertThat(key).isEqualTo("ip:unknown");
    }

    private static reactor.util.context.Context withJwtSubject(String subject) {
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt(subject),
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")), subject);
        return ReactiveSecurityContextHolder.withSecurityContext(
                reactor.core.publisher.Mono.just(new SecurityContextImpl(auth)));
    }
}
