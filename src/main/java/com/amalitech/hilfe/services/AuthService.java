package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.RefreshTokenRequest;
import com.amalitech.hilfe.dto.TokenResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final ArmsClient armsClient;
    private final ArmsTokenExpiryService armsTokenExpiryService;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    @Transactional
    public TokenResponse login(LoginRequest request) {
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

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(tokenService.getAccessTokenTtlSeconds())
                .refreshTokenExpiresIn(refreshTokenTtlSeconds)
                .build();
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request, String armsToken) {
        TokenService.RefreshPrincipal refreshPrincipal = tokenService.authenticateRefreshToken(request.refreshToken())
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
        String refreshToken = tokenService.generateRefreshToken(user, refreshTokenTtlSeconds);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(tokenService.getAccessTokenTtlSeconds())
                .refreshTokenExpiresIn(refreshTokenTtlSeconds)
                .build();
    }

    public void logout(RefreshTokenRequest request) {
        // TODO: Revoke refresh token when storage is implemented.
    }

    private void validateRefreshPrincipal(TokenService.RefreshPrincipal refreshPrincipal, ArmsUserInfo armsUser) {
        if (!refreshPrincipal.userId().equals(armsUser.userId())
                || !refreshPrincipal.email().equals(armsUser.email())) {
            throw new ArmsAuthException("Refresh token does not match the authenticated ARMS user", 401);
        }
    }

}
