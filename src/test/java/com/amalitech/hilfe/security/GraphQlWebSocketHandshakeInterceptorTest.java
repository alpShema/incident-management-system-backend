package com.amalitech.hilfe.security;

import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.services.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphQlWebSocketHandshakeInterceptorTest {

    @Mock TokenService tokenService;

    private UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:5173"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private GraphQlWebSocketHandshakeInterceptor interceptor() {
        return new GraphQlWebSocketHandshakeInterceptor(tokenService, corsConfigurationSource());
    }

    @Test
    void missingOrigin_rejectsHandshakeWith403() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/graphql");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(response),
                null, attributes);

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(attributes).doesNotContainKey(GraphQlWebSocketHandshakeInterceptor.SECURITY_CONTEXT_ATTRIBUTE);
    }

    @Test
    void disallowedOrigin_rejectsHandshakeWith403() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/graphql");
        request.addHeader("Origin", "https://evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(response),
                null, attributes);

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void allowedOrigin_noCookie_permitsHandshakeWithoutSecurityContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/graphql");
        request.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(response),
                null, attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes).doesNotContainKey(GraphQlWebSocketHandshakeInterceptor.SECURITY_CONTEXT_ATTRIBUTE);
    }

    @Test
    void allowedOrigin_validCookie_permitsHandshakeAndStashesSecurityContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/graphql");
        request.addHeader("Origin", "http://localhost:5173");
        request.setCookies(new Cookie("access_token", "valid-arms-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        User user = User.builder().id("u1").email("john@test.com").build();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(tokenService.authenticateAccessToken("valid-arms-token")).thenReturn(Optional.of(auth));

        boolean allowed = interceptor().beforeHandshake(
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(response),
                null, attributes);

        assertThat(allowed).isTrue();
        SecurityContext stashed = (SecurityContext) attributes.get(GraphQlWebSocketHandshakeInterceptor.SECURITY_CONTEXT_ATTRIBUTE);
        assertThat(stashed).isNotNull();
        assertThat(stashed.getAuthentication().getPrincipal()).isEqualTo(user);
    }
}
