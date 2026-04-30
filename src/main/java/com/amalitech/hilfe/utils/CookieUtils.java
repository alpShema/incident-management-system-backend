package com.amalitech.hilfe.utils;

import com.amalitech.hilfe.auth.dto.TokenResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * Owner: Lawson
 * Depends on: TokenResponse cookie TTL values.
 */
public final class CookieUtils {
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private CookieUtils() {}

    public static void addAuthCookies(HttpServletResponse response, TokenResponse tokens, boolean secure) {
        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, tokens.getAccessToken())
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .maxAge(tokens.getAccessTokenExpiresIn())
            .build();

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokens.getRefreshToken())
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .maxAge(tokens.getRefreshTokenExpiresIn())
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public static void clearAuthCookies(HttpServletResponse response, boolean secure) {
        ResponseCookie accessCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build();

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public static String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
