package com.amalitech.hilfe.utils;

import com.amalitech.hilfe.dto.AuthTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * Owner: Lawson
 * Depends on: AuthTokens cookie TTL values.
 */
public final class CookieUtils {
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String ARMS_TOKEN_COOKIE = "arms_token";

    private CookieUtils() {
    }

    public static void addAuthCookies(HttpServletResponse response, AuthTokens tokens, boolean secure) {
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

    /**
     * Stores the original ARMS token in its own httpOnly cookie.
     * The backend uses this for subsequent server-to-server ARMS calls on behalf of the user.
     * TTL should match the remaining ARMS token lifetime.
     */
    public static void addArmsTokenCookie(HttpServletResponse response, String armsToken,
                                          boolean secure, long ttlSeconds) {
        ResponseCookie armsCookie = ResponseCookie.from(ARMS_TOKEN_COOKIE, armsToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(ttlSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, armsCookie.toString());
    }

    /**
     * Clears all three auth cookies: access_token, refresh_token, arms_token.
     */
    public static void clearAuthCookies(HttpServletResponse response, boolean secure) {
        for (String name : new String[]{ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE, ARMS_TOKEN_COOKIE}) {
            ResponseCookie cleared = ResponseCookie.from(name, "")
                    .httpOnly(true)
                    .secure(secure)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(0)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cleared.toString());
        }
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
