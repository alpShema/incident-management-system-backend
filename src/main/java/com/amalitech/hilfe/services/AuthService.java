package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.dto.AuthResult;
import com.amalitech.hilfe.dto.AuthSessionResponse;
import com.amalitech.hilfe.dto.AuthTokens;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.UserPermissionsResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final ArmsClient armsClient;
    private final ArmsTokenExpiryService armsTokenExpiryService;
    private final TokenService tokenService;
    private final TokenRevocationService tokenRevocationService;
    private final UserRepository userRepository;
    private final UserAuthorityService userAuthorityService;
    @Transactional
    public AuthResult login(LoginRequest request) {
        ArmsUserInfo armsUser = armsClient.getUserByToken(request.armsToken());

        userRepository.upsert(
                armsUser.userId(),
                armsUser.email(),
                armsUser.firstName() + " " + armsUser.lastName(),
                armsUser.profileImage()
        );

        User user = userRepository.findAuthUserById(armsUser.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after upsert for id=" + armsUser.userId()));

        long refreshTokenTtlSeconds = armsTokenExpiryService.getRemainingLifetimeSeconds(request.armsToken());
        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user, refreshTokenTtlSeconds);

        return new AuthResult(
                AuthTokens.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(tokenService.getAccessTokenTtlSeconds())
                .refreshTokenExpiresIn(refreshTokenTtlSeconds)
                .build(),
                toSessionResponse(user)
        );
    }

    @Transactional
    public AuthResult refresh(String refreshToken, String armsToken) {
        TokenService.RefreshPrincipal refreshPrincipal = tokenService.authenticateRefreshToken(refreshToken)
                .orElseThrow(() -> new ArmsAuthException("Invalid refresh token", 401));

        if (tokenRevocationService.isRevoked(refreshPrincipal.jti())) {
            throw new ArmsAuthException("Refresh token has been revoked", 401);
        }

        long refreshTokenTtlSeconds = armsTokenExpiryService.getRemainingLifetimeSeconds(armsToken);
        ArmsUserInfo armsUser = armsClient.getUserByToken(armsToken);
        validateRefreshPrincipal(refreshPrincipal, armsUser);

        userRepository.upsert(
                armsUser.userId(),
                armsUser.email(),
                armsUser.firstName() + " " + armsUser.lastName(),
                armsUser.profileImage()
        );

        User user = userRepository.findAuthUserById(armsUser.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after upsert for id=" + armsUser.userId()));

        tokenRevocationService.revoke(
                refreshPrincipal.jti(), refreshPrincipal.userId(), refreshPrincipal.expiresAt());

        String accessToken = tokenService.generateAccessToken(user);
        String newRefreshToken = tokenService.generateRefreshToken(user, refreshTokenTtlSeconds);

        return new AuthResult(
                AuthTokens.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .accessTokenExpiresIn(tokenService.getAccessTokenTtlSeconds())
                .refreshTokenExpiresIn(refreshTokenTtlSeconds)
                .build(),
                toSessionResponse(user)
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.debug("Logout called with no refresh token — nothing to revoke");
            return;
        }
        tokenService.authenticateRefreshToken(refreshToken)
                .ifPresentOrElse(
                        p -> {
                            tokenRevocationService.revoke(p.jti(), p.userId(), p.expiresAt());
                            userRepository.incrementTokenVersion(p.userId());
                        },
                        () -> log.debug("Logout: refresh token invalid or expired — nothing to revoke")
                );
    }

    public UserPermissionsResponse getUserPermissions(String userId) {
        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolveByUserId(userId)
                .orElseThrow(() -> new ArmsAuthException("Authenticated user not found", 401));

        List<String> permissions = resolvedAuthorities.authorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("ROLE_"))
                .sorted(Comparator.naturalOrder())
                .toList();

        return UserPermissionsResponse.builder()
                .userId(resolvedAuthorities.userId())
                .permissions(permissions)
                .build();
    }

    private void validateRefreshPrincipal(TokenService.RefreshPrincipal refreshPrincipal, ArmsUserInfo armsUser) {
        if (!refreshPrincipal.userId().equals(armsUser.userId())
                || !refreshPrincipal.email().equals(armsUser.email())) {
            throw new ArmsAuthException("Refresh token does not match the authenticated ARMS user", 401);
        }
    }

    private AuthSessionResponse toSessionResponse(User user) {
        return AuthSessionResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .profileImg(user.getProfileImg())
                .build();
    }

}
