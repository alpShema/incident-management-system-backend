package com.amalitech.hilfe.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Owner: Basit
 * Depends on: JwtAuthenticationFilter and Spring Security configuration.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final Http401AuthenticationEntryPoint authenticationEntryPoint;

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${cors.extra-allowed-origins:}")
    private List<String> extraAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception { // NOSONAR java:S112 - throws Exception is required by the Spring Security HttpSecurity API
        http
                .csrf(csrf -> csrf.disable()) // NOSONAR java:S4502 - stateless REST API using HttpOnly JWT cookies; CSRF not applicable
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/refresh-token", "/auth/logout").permitAll()
                        .requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/graphiql/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> mergedOrigins = new ArrayList<>(allowedOrigins);
        if (extraAllowedOrigins != null) {
            extraAllowedOrigins.stream()
                    .filter(o -> !o.isBlank())
                    .forEach(mergedOrigins::add);
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(mergedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Required for the browser to send cookies (credentials: include) cross-origin
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        // WebSocket upgrade requests must pass the security CORS filter before reaching
        // WebSocketConfig's setAllowedOriginPatterns("*"), so allow all origins here too.
        CorsConfiguration wsConfig = new CorsConfiguration();
        wsConfig.setAllowedOriginPatterns(List.of("*")); // NOSONAR java:S5122 - intentionally permissive to match WebSocketConfig; security is enforced by JwtHandshakeInterceptor (JWT token validation on every upgrade)
        wsConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        wsConfig.setAllowedHeaders(List.of("*"));
        wsConfig.setAllowCredentials(true);
        wsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/ws/**", wsConfig);
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
