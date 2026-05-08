package com.amalitech.hilfe.utils;

import com.amalitech.hilfe.dto.AuthTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilsTest {

    @Test
    void addAuthCookies_setsAccessAndRefreshCookieHeaders() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthTokens tokens = AuthTokens.builder()
                .accessToken("at-value").refreshToken("rt-value")
                .accessTokenExpiresIn(3600L).refreshTokenExpiresIn(86400L)
                .build();

        CookieUtils.addAuthCookies(response, tokens, false);

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0)).contains("access_token=at-value")
                .contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=3600");
        assertThat(cookies.get(1)).contains("refresh_token=rt-value")
                .contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=86400");
    }

    @Test
    void addAuthCookies_secureFlag_setsCookieWithSecure() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthTokens tokens = AuthTokens.builder()
                .accessToken("at").refreshToken("rt")
                .accessTokenExpiresIn(3600L).refreshTokenExpiresIn(86400L)
                .build();

        CookieUtils.addAuthCookies(response, tokens, true);

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).allSatisfy(c -> assertThat(c).contains("Secure"));
    }

    @Test
    void addArmsTokenCookie_setsArmsTokenHeader() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtils.addArmsTokenCookie(response, "arms-token-val", false, 3600L);

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.get(0)).contains("arms_token=arms-token-val")
                .contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=3600");
    }

    @Test
    void clearAuthCookies_setsAllThreeCookiesToExpired() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtils.clearAuthCookies(response, false);

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies).allSatisfy(c -> assertThat(c).contains("Max-Age=0"));
        assertThat(cookies.get(0)).contains("access_token=");
        assertThat(cookies.get(1)).contains("refresh_token=");
        assertThat(cookies.get(2)).contains("arms_token=");
    }

    @Test
    void getCookieValue_cookiePresent_returnsValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "jwt-value"));

        String value = CookieUtils.getCookieValue(request, "access_token");

        assertThat(value).isEqualTo("jwt-value");
    }

    @Test
    void getCookieValue_cookieAbsent_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other_cookie", "val"));

        String value = CookieUtils.getCookieValue(request, "access_token");

        assertThat(value).isNull();
    }

    @Test
    void getCookieValue_noCookies_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String value = CookieUtils.getCookieValue(request, "access_token");

        assertThat(value).isNull();
    }
}
