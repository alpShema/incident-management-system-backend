package com.amalitech.hilfe.utils;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilsTest {

    @Test
    void addSessionCookie_setsSingleAccessTokenCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtils.addSessionCookie(response, "arms-token-val", 3600L, false, "Lax");

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.get(0)).contains("access_token=arms-token-val")
                .contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=3600");
    }

    @Test
    void addSessionCookie_secureFlag_setsCookieWithSecure() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtils.addSessionCookie(response, "arms-token-val", 3600L, true, "Lax");

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotEmpty().allSatisfy(c -> assertThat(c).contains("Secure"));
    }

    @Test
    void clearAuthCookies_setsAccessTokenCookieToExpired() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtils.clearAuthCookies(response, false, "Lax");

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.get(0)).contains("access_token=").contains("Max-Age=0");
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
