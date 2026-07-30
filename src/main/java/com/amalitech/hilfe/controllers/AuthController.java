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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Authentication", description = "ARMS SSO login, logout, and permission retrieval")
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
        description = "Authenticates the user using an ARMS SSO token. On success, sets a single HttpOnly session cookie holding that token and returns session info."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired ARMS token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthResult authResult = authService.login(request);
        CookieUtils.addSessionCookie(response, request.armsToken(), authResult.sessionTtlSeconds(), cookieSecure, cookieSameSite);
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResult.session()));
    }

    @Operation(
        summary = "Logout",
        description = "Invalidates the current session and clears the session cookie."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String armsToken = CookieUtils.getCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE);
        try {
            authService.logout(armsToken);
        } catch (Exception ex) {
            log.error("Failed to revoke session during logout; clearing cookie anyway", ex);
        }
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
