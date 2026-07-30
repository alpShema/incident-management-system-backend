package com.amalitech.hilfe.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * Owner: Lawson
 * Depends on: the raw ARMS token as the sole session credential.
 */
public final class CookieUtils {
    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    private CookieUtils() {
    }

    /**
     * Sets the single HttpOnly session cookie, holding the raw ARMS token, with a maxAge
     * matching the ARMS token's own remaining lifetime.
     */
    public static void addSessionCookie(HttpServletResponse response, String armsToken,
                                         long ttlSeconds, boolean secure, String sameSite) {
        ResponseCookie sessionCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, armsToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(ttlSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
    }

    /**
     * Clears the session cookie.
     */
    public static void clearAuthCookies(HttpServletResponse response, boolean secure, String sameSite) {
        ResponseCookie cleared = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cleared.toString());
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
