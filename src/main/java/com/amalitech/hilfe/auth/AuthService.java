package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.auth.dto.ArmsUserInfo;
import com.amalitech.hilfe.auth.dto.LoginRequest;
import com.amalitech.hilfe.auth.dto.RefreshTokenRequest;
import com.amalitech.hilfe.auth.dto.TokenResponse;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Owner: Lawson
 * Depends on: ArmsClient (Alphone), TokenService (Basit), UserRepository.
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final ArmsClient armsClient;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    /**
     * Owner: Lawson
     * Depends on: ArmsClient.getUserByToken, UserRepository, TokenService.
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        ArmsUserInfo armsUser = armsClient.getUserByToken(request.armsToken());

        // Atomic upsert — avoids the race condition of find-then-save on concurrent logins.
        userRepository.upsert(
                armsUser.userId(),
                armsUser.email(),
                armsUser.firstName() + " " + armsUser.lastName(),
                armsUser.profileImage()
        );

        User user = userRepository.findById(armsUser.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after upsert for id=" + armsUser.userId()));

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user);

        return TokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .accessTokenExpiresIn(tokenService.getAccessTokenTtlSeconds())
            .refreshTokenExpiresIn(tokenService.getRefreshTokenTtlSeconds())
            .build();
    }

    /**
     * Owner: Lawson
     * Depends on: refresh token storage and TokenService.
     */
    public TokenResponse refresh(RefreshTokenRequest request) {
        throw new UnsupportedOperationException("Refresh token flow not implemented yet");
    }

    /**
     * Owner: Lawson
     * Depends on: refresh token revocation implementation.
     */
    public void logout(RefreshTokenRequest request) {
        // TODO: Revoke refresh token when storage is implemented.
    }
}
