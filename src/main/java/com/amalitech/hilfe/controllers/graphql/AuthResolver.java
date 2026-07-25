package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.AuthResult;
import com.amalitech.hilfe.dto.AuthSessionResponse;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.UserPermissionsResponse;
import com.amalitech.hilfe.services.AuthService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.utils.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Controller
@RequiredArgsConstructor
public class AuthResolver {

    private final AuthService authService;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${cookie.same-site:None}")
    private String cookieSameSite;

    @MutationMapping
    public AuthSessionResponse login(@Valid @Argument LoginInput input) {
        LoginRequest request = new LoginRequest(input.armsToken());
        AuthResult result = authService.login(request);
        HttpServletResponse response = currentResponse();
        CookieUtils.addAuthCookies(response, result.tokens(), cookieSecure, cookieSameSite);
        CookieUtils.addArmsTokenCookie(response, input.armsToken(), cookieSecure, cookieSameSite,
                result.tokens().getRefreshTokenExpiresIn());
        GraphQlResponseMessage.set("Login successful");
        return result.session();
    }

    @MutationMapping
    public AuthSessionResponse refreshToken() {
        HttpServletRequest request = currentRequest();
        HttpServletResponse response = currentResponse();
        String armsToken = CookieUtils.getCookieValue(request, CookieUtils.ARMS_TOKEN_COOKIE);
        String refreshToken = CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE);
        AuthResult result = authService.refresh(refreshToken, armsToken);
        CookieUtils.addAuthCookies(response, result.tokens(), cookieSecure, cookieSameSite);
        CookieUtils.addArmsTokenCookie(response, armsToken, cookieSecure, cookieSameSite,
                result.tokens().getRefreshTokenExpiresIn());
        GraphQlResponseMessage.set("Token refreshed successfully");
        return result.session();
    }

    @MutationMapping
    public boolean logout() {
        HttpServletRequest request = currentRequest();
        HttpServletResponse response = currentResponse();
        String refreshToken = CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE);
        authService.logout(refreshToken);
        CookieUtils.clearAuthCookies(response, cookieSecure, cookieSameSite);
        GraphQlResponseMessage.set("Logout successful");
        return true;
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public UserPermissionsResponse myPermissions(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return authService.getUserPermissions(principal.userId());
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest();
    }

    private static HttpServletResponse currentResponse() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getResponse();
    }

    public record LoginInput(String armsToken) {}
}
