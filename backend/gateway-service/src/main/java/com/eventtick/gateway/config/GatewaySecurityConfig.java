package com.eventtick.gateway.config;

import com.eventtick.gateway.exception.GatewayErrorWriter;
import com.eventtick.gateway.security.GatewayJwtAuthenticationConverter;
import com.eventtick.gateway.security.GatewayJwtDecoder;
import com.eventtick.gateway.security.JsonAccessDeniedHandler;
import com.eventtick.gateway.security.JsonAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Phase 7.3: JWT authentication at the gateway. user-service remains the
 * only thing that <i>issues</i> tokens; the gateway validates them before a
 * protected request is forwarded, so an invalid request never reaches a
 * backend.
 *
 * <p>Two chains, first match wins:
 * <ol>
 *   <li><b>Public</b> — exactly {@code POST /api/auth/register} and
 *   {@code POST /api/auth/login}. This chain never reads an
 *   {@code Authorization} header, so a stale or garbage token left in a
 *   browser can't make login or registration fail (Spring's bearer
 *   filter would otherwise reject the bad token even on a public path).
 *   <li><b>Protected</b> — everything else, deny-by-default: a valid JWT is
 *   required, so a new route is protected unless someone deliberately adds
 *   it to the public list above. This includes {@code /api/users/me}, and
 *   for now also {@code /api/catalog/**} and {@code /api/bookings/**}.
 * </ol>
 *
 * <p><b>Authorization.</b> {@code /api/admin/**} additionally requires the
 * {@code ROLE_ADMIN} authority (Phase 13.3) — a {@code CUSTOMER} token gets
 * {@code 403} from {@link JsonAccessDeniedHandler}, not {@code 401}: it
 * authenticated successfully, it just isn't allowed here. Everywhere else,
 * there are no ownership rules at the Gateway (resource ownership stays
 * with the owning service — see architecture §6.3). The one other role rule:
 * write methods (POST/PUT/PATCH/DELETE) on the four catalog management
 * resources ({@code content}, {@code venues}, {@code seats}, {@code shows})
 * require {@code ROLE_ADMIN}; reads there need only a valid token. The {@code role} claim is exposed as a {@code ROLE_*} authority for
 * exactly this kind of rule.
 *
 * <p>The {@code Authorization} header is forwarded to the backend
 * unchanged; user-service still validates it itself for {@code /me}
 * (defense in depth). No identity headers are added — that would need
 * stripping client-supplied copies first, which is out of scope here.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /**
     * The catalog resources a customer may read but not change (Phase 15
     * Step 4 follow-up: previously any authenticated customer could create,
     * update and delete them through {@code /api/catalog/**}). Listed
     * explicitly rather than as {@code /api/catalog/**} so a future catalog
     * path is not silently swept into an admin rule.
     */
    private static final String[] CATALOG_MANAGEMENT_PATHS = {
            "/api/catalog/content", "/api/catalog/content/**",
            "/api/catalog/venues", "/api/catalog/venues/**",
            "/api/catalog/seats", "/api/catalog/seats/**",
            "/api/catalog/shows", "/api/catalog/shows/**"
    };

    /**
     * Built from the existing {@code spring.cloud.gateway.globalcors} config
     * (Phase 7.2) rather than duplicated. It is needed here because the
     * security filters run <i>before</i> the gateway's own CORS handling, so
     * without it a 401 from this layer would carry no CORS headers — the
     * browser would then hide the response and the frontend could not tell
     * an expired token (401, clear it) from a network failure (keep it).
     * Also answers preflight requests before any authentication check.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(GlobalCorsProperties globalCors) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.setCorsConfigurations(globalCors.getCorsConfigurations());
        return source;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience) {
        return GatewayJwtDecoder.create(secret, issuer, audience);
    }

    @Bean
    @Order(1)
    public SecurityWebFilterChain publicAuthEndpoints(ServerHttpSecurity http, CorsConfigurationSource cors) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        HttpMethod.POST, "/api/auth/register", "/api/auth/login"))
                .cors(c -> c.configurationSource(cors))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain protectedEndpoints(ServerHttpSecurity http, CorsConfigurationSource cors,
                                                     ReactiveJwtDecoder jwtDecoder, GatewayErrorWriter errors) {
        JsonAuthenticationEntryPoint unauthorized = new JsonAuthenticationEntryPoint(errors);
        JsonAccessDeniedHandler forbidden = new JsonAccessDeniedHandler(errors);
        return http
                .cors(c -> c.configurationSource(cors))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        // Phase 14 Step 1: narrow allowlist, not /actuator/**
                        // — only health/liveness/readiness/prometheus are
                        // exposed at all (see management.endpoints.web.exposure
                        // in application.yml), and only these exact paths skip
                        // the JWT requirement below. Handled by this same
                        // gateway-service process — there is deliberately no
                        // spring.cloud.gateway.routes entry for /actuator/**,
                        // so nothing here is ever forwarded to an upstream.
                        .pathMatchers(HttpMethod.GET,
                                "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness", "/actuator/prometheus")
                        .permitAll()
                        // Phase 13.3: checked before the blanket rule below, so an
                        // authenticated CUSTOMER gets 403 (authenticated, not
                        // authorized) rather than the generic authenticated() pass.
                        .pathMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        // Booking confirmation is payment-service's internal job
                        // (POST /internal/bookings/{id}/confirm, which has no
                        // Gateway route). The old public endpoint is denied for
                        // every role, admin included, and never forwarded.
                        .pathMatchers("/api/bookings/*/confirm").denyAll()
                        // Catalog management is admin-only. Reads under
                        // /api/catalog/** stay open to any authenticated user;
                        // only writes on these four resources need ROLE_ADMIN.
                        .pathMatchers(HttpMethod.POST, CATALOG_MANAGEMENT_PATHS).hasAuthority("ROLE_ADMIN")
                        .pathMatchers(HttpMethod.PUT, CATALOG_MANAGEMENT_PATHS).hasAuthority("ROLE_ADMIN")
                        .pathMatchers(HttpMethod.PATCH, CATALOG_MANAGEMENT_PATHS).hasAuthority("ROLE_ADMIN")
                        .pathMatchers(HttpMethod.DELETE, CATALOG_MANAGEMENT_PATHS).hasAuthority("ROLE_ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(new GatewayJwtAuthenticationConverter())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .build();
    }
}
