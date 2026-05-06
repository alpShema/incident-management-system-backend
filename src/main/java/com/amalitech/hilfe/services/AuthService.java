package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.dto.AuthResult;
import com.amalitech.hilfe.dto.AuthSessionResponse;
import com.amalitech.hilfe.dto.AuthTokens;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.UserPermissionsResponse;
import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final ArmsClient armsClient;
    private final ArmsTokenExpiryService armsTokenExpiryService;
    private final TokenService tokenService;
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

    public void logout() {
        // TODO: Revoke refresh token when storage is implemented.
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

    public Page<UserRoleSummaryResponse> getUserRoles(Pageable pageable) {
        return userRepository.findUserRoleSummaries(pageable);
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
