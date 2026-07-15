package com.amalitech.hilfe.services;

import com.amalitech.hilfe.constants.ApiMessages;
import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.mappers.ArmsUserMapper;
import com.amalitech.hilfe.models.Location;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.LocationRepository;
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
    private static final String ACCOUNT_DEACTIVATED_MESSAGE = "Your account has been deactivated. Please contact your administrator.";

    private final ArmsClient armsClient;
    private final ArmsTokenExpiryService armsTokenExpiryService;
    private final TokenService tokenService;
    private final TokenRevocationService tokenRevocationService;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final UserAuthorityService userAuthorityService;
    @Transactional
    public AuthResult login(LoginRequest request) {
        ArmsUserInfo armsUser = armsClient.getUserByToken(request.armsToken());
        String locationId = resolveLocationId(armsUser);

        userRepository.upsert(
                armsUser.userId(),
                armsUser.email(),
                ArmsUserMapper.buildFullName(armsUser),
                armsUser.phoneNumber(),
                armsUser.profileImage(),
                armsUser.positionName(),
                locationId
        );

        User user = userRepository.findAuthUserById(armsUser.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after upsert for id=" + armsUser.userId()));

        if (Boolean.FALSE.equals(user.getStatus())) {
            throw new ArmsAuthException(ACCOUNT_DEACTIVATED_MESSAGE, 403);
        }

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
                .orElseThrow(() -> new ArmsAuthException(ApiMessages.SESSION_EXPIRED, 401));

        if (tokenRevocationService.isRevoked(refreshPrincipal.jti())) {
            throw new ArmsAuthException(ApiMessages.SESSION_EXPIRED, 401);
        }

        long refreshTokenTtlSeconds = armsTokenExpiryService.getRemainingLifetimeSeconds(armsToken);
        ArmsUserInfo armsUser = armsClient.getUserByToken(armsToken);
        validateRefreshPrincipal(refreshPrincipal, armsUser);

        String locationId = resolveLocationId(armsUser);
        userRepository.upsert(
                armsUser.userId(),
                armsUser.email(),
                ArmsUserMapper.buildFullName(armsUser),
                armsUser.phoneNumber(),
                armsUser.profileImage(),
                armsUser.positionName(),
                locationId
        );

        User user = userRepository.findAuthUserById(armsUser.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after upsert for id=" + armsUser.userId()));

        if (Boolean.FALSE.equals(user.getStatus())) {
            throw new ArmsAuthException(ACCOUNT_DEACTIVATED_MESSAGE, 403);
        }

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
                .orElseThrow(() -> new ArmsAuthException("We could not find your account. Please log in again.", 401));

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
            throw new ArmsAuthException(ApiMessages.SESSION_UNVERIFIABLE, 401);
        }
    }

    private String resolveLocationId(ArmsUserInfo armsUser) {
        if (armsUser.officeName() == null || armsUser.officeName().isBlank()) {
            return null;
        }
        return locationRepository.findByNameIgnoreCase(armsUser.officeName())
                .or(() -> locationRepository.findByNameIgnoreCase(normalizeOfficeName(armsUser.officeName())))
                .map(Location::getId)
                .orElse(null);
    }

    private String normalizeOfficeName(String officeName) {
        String suffix = "office";
        int cut = officeName.length() - suffix.length();
        if (cut > 0
                && officeName.regionMatches(true, cut, suffix, 0, suffix.length())
                && Character.isWhitespace(officeName.charAt(cut - 1))) {
            return officeName.substring(0, cut).trim();
        }
        return officeName.trim();
    }

    private AuthSessionResponse toSessionResponse(User user) {
        List<String> permissions = userAuthorityService.resolveByUserId(user.getId())
                .map(resolved -> resolved.authorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> a != null && !a.startsWith("ROLE_"))
                        .sorted(Comparator.naturalOrder())
                        .toList())
                .orElse(List.of());

        return AuthSessionResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .profileImg(user.getProfileImg())
                .role(user.getRoleCode())
                .permissions(permissions)
                .build();
    }

}
