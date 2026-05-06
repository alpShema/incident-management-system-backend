package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.AuthResult;
import com.amalitech.hilfe.dto.AuthSessionResponse;
import com.amalitech.hilfe.dto.UserPermissionsResponse;
import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.services.AuthService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthResult authResult = authService.login(request);
        CookieUtils.addAuthCookies(response, authResult.tokens(), cookieSecure);
        CookieUtils.addArmsTokenCookie(response, request.armsToken(), cookieSecure,
                authResult.tokens().getRefreshTokenExpiresIn());
        return ResponseEntity.ok(authResult.session());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthSessionResponse> refresh(
            HttpServletRequest servletRequest,
            HttpServletResponse response
    ) {
        String armsToken = CookieUtils.getCookieValue(servletRequest, CookieUtils.ARMS_TOKEN_COOKIE);
        String refreshToken = CookieUtils.getCookieValue(servletRequest, CookieUtils.REFRESH_TOKEN_COOKIE);
        AuthResult authResult = authService.refresh(refreshToken, armsToken);
        CookieUtils.addAuthCookies(response, authResult.tokens(), cookieSecure);
        CookieUtils.addArmsTokenCookie(response, armsToken, cookieSecure, authResult.tokens().getRefreshTokenExpiresIn());
        return ResponseEntity.ok(authResult.session());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletResponse response
    ) {
        authService.logout();
        CookieUtils.clearAuthCookies(response, cookieSecure);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/permissions")
    public ResponseEntity<UserPermissionsResponse> permissions(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return ResponseEntity.ok(authService.getUserPermissions(principal.userId()));
    }

    @GetMapping("/users/roles")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Page<UserRoleSummaryResponse>> userRoles(Pageable pageable) {
        return ResponseEntity.ok(authService.getUserRoles(pageable));
    }
}
