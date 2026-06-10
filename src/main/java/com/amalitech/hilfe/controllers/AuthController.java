package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.services.AuthService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.utils.CookieUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "ARMS SSO login, token refresh, logout, and permission retrieval")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${cookie.same-site:None}")
    private String cookieSameSite;

    @Operation(
        summary = "Login with ARMS token",
        description = "Authenticates the user using an ARMS SSO token. On success, sets HttpOnly `access_token`, `refresh_token`, and `arms_token` cookies and returns session info."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired ARMS token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthResult authResult = authService.login(request);
        CookieUtils.addAuthCookies(response, authResult.tokens(), cookieSecure, cookieSameSite);
        CookieUtils.addArmsTokenCookie(response, request.armsToken(), cookieSecure, cookieSameSite,
                authResult.tokens().getRefreshTokenExpiresIn());
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResult.session()));
    }

    @Operation(
        summary = "Refresh access token",
        description = "Issues a new access token using the `refresh_token` cookie. Also re-sets the `arms_token` cookie. No request body required."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or expired refresh token")
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> refresh(
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        String armsToken = CookieUtils.getCookieValue(servletRequest, CookieUtils.ARMS_TOKEN_COOKIE);
        String refreshToken = CookieUtils.getCookieValue(servletRequest, CookieUtils.REFRESH_TOKEN_COOKIE);
        AuthResult authResult = authService.refresh(refreshToken, armsToken);
        CookieUtils.addAuthCookies(response, authResult.tokens(), cookieSecure, cookieSameSite);
        CookieUtils.addArmsTokenCookie(response, armsToken, cookieSecure, cookieSameSite, authResult.tokens().getRefreshTokenExpiresIn());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResult.session()));
    }

    @Operation(
        summary = "Logout",
        description = "Invalidates the current session and clears all auth cookies (`access_token`, `refresh_token`, `arms_token`)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE);
        authService.logout(refreshToken);
        CookieUtils.clearAuthCookies(response, cookieSecure, cookieSameSite);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(
        summary = "Get current user permissions",
        description = "Returns the authenticated user's role and the full list of granted permissions derived from that role."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Permissions retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<UserPermissionsResponse>> permissions(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved successfully", authService.getUserPermissions(principal.userId())));
    }
}
