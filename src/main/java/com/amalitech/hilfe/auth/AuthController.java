package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.auth.dto.LoginRequest;
import com.amalitech.hilfe.auth.dto.RefreshTokenRequest;
import com.amalitech.hilfe.auth.dto.TokenResponse;
import com.amalitech.hilfe.utils.CookieUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner: Lawson
 * Depends on: AuthService and CookieUtils.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    /** Owner: Lawson. Depends on: AuthService.login, CookieUtils. */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
        @RequestBody LoginRequest request,
        HttpServletResponse response
    ) {
        TokenResponse tokens = authService.login(request);
        CookieUtils.addAuthCookies(response, tokens, cookieSecure);
        CookieUtils.addArmsTokenCookie(response, request.armsToken(), cookieSecure,
                tokens.getAccessTokenExpiresIn());
        return ResponseEntity.ok(tokens);
    }

    /** Owner: Lawson. Depends on: AuthService.refresh, CookieUtils. */
    @PostMapping("/refresh-token")
    public ResponseEntity<TokenResponse> refresh(
        @RequestBody RefreshTokenRequest request,
        HttpServletResponse response
    ) {
        TokenResponse tokens = authService.refresh(request);
        CookieUtils.addAuthCookies(response, tokens, cookieSecure);
        return ResponseEntity.ok(tokens);
    }

    /** Owner: Lawson. Depends on: AuthService.logout, CookieUtils. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestBody RefreshTokenRequest request,
        HttpServletResponse response
    ) {
        authService.logout(request);
        CookieUtils.clearAuthCookies(response, cookieSecure);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
