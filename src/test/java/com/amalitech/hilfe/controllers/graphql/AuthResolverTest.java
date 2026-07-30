package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.AuthResult;
import com.amalitech.hilfe.dto.AuthSessionResponse;
import com.amalitech.hilfe.services.AuthService;
import com.amalitech.hilfe.utils.CookieUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// AuthResolver is the GraphQL mirror of AuthController — previously missed entirely when the
// service was migrated to authorize directly against the ARMS token instead of minting its own
// JWT, which would have left this file calling deleted AuthService/CookieUtils methods.
@GraphQlTest(AuthResolver.class)
@Import({AuthResolverTest.MethodSecurityTestConfig.class, GraphQlConfig.class})
@TestPropertySource(properties = {"cookie.secure=false", "cookie.same-site=Lax"})
class AuthResolverTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean AuthService authService;

    MockHttpServletRequest servletRequest;
    MockHttpServletResponse servletResponse;

    @BeforeEach
    void bindRequestContext() {
        servletRequest = new MockHttpServletRequest();
        servletResponse = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest, servletResponse));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void login_validArmsToken_setsSingleCookieAndReturnsSession() {
        AuthResult result = new AuthResult(7200L, AuthSessionResponse.builder()
                .userId("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .role("CLIENT")
                .roleName("Client")
                .build());
        when(authService.login(any())).thenReturn(result);

        String mutation = """
                mutation($armsToken: String!) {
                  login(input: { armsToken: $armsToken }) {
                    userId
                    email
                    roleName
                  }
                }
                """;

        graphQlTester.document(mutation)
                .variable("armsToken", "arms-token-val")
                .execute()
                .path("login.userId").entity(String.class).isEqualTo("u1")
                .path("login.email").entity(String.class).isEqualTo("john@test.com")
                .path("login.roleName").entity(String.class).isEqualTo("Client");

        List<String> cookies = servletResponse.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies)
                .hasSize(1)
                .anyMatch(c -> c.startsWith("access_token=arms-token-val"))
                .allSatisfy(c -> assertThat(c).contains("HttpOnly").contains("Max-Age=7200"));
    }

    @Test
    void logout_withSessionCookie_passesTokenToServiceAndClearsCookie() {
        servletRequest.setCookies(new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, "arms-token-value"));

        String mutation = "mutation { logout }";

        graphQlTester.document(mutation)
                .execute()
                .path("logout").entity(Boolean.class).isEqualTo(true);

        verify(authService).logout("arms-token-value");
        List<String> cookies = servletResponse.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies)
                .hasSize(1)
                .allSatisfy(c -> assertThat(c).contains("access_token=").contains("Max-Age=0"));
    }

    @Test
    void logout_revocationThrows_stillClearsCookie() {
        servletRequest.setCookies(new Cookie(CookieUtils.ACCESS_TOKEN_COOKIE, "arms-token-value"));
        doThrow(new RuntimeException("db unavailable")).when(authService).logout("arms-token-value");

        String mutation = "mutation { logout }";

        graphQlTester.document(mutation)
                .execute()
                .path("logout").entity(Boolean.class).isEqualTo(true);

        List<String> cookies = servletResponse.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies)
                .hasSize(1)
                .allSatisfy(c -> assertThat(c).contains("access_token=").contains("Max-Age=0"));
    }
}
