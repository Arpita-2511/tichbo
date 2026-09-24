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
 * there are still no role or ownership rules: any valid token, CUSTOMER or
 * ADMIN, passes. Who may do what on catalog/booking is a later decision
 * (and resource ownership stays with the owning service — see architecture
 * §6.3). The {@code role} claim is exposed as a {@code ROLE_*} authority for
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
                        // Phase 13.3: checked before the blanket rule below, so an
                        // authenticated CUSTOMER gets 403 (authenticated, not
                        // authorized) rather than the generic authenticated() pass.
                        .pathMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
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
