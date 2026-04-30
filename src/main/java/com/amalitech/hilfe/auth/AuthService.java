package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.auth.dto.ArmsUserInfo;
import com.amalitech.hilfe.auth.dto.LoginRequest;
import com.amalitech.hilfe.auth.dto.RefreshTokenRequest;
import com.amalitech.hilfe.auth.dto.TokenResponse;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
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
    public TokenResponse login(LoginRequest request) {
        ArmsUserInfo armsUser = armsClient.getUserByToken(request.armsToken());

        User user = userRepository.findById(armsUser.userId())
            .orElseGet(() -> User.builder().id(armsUser.userId()).build());

        user.setEmail(armsUser.email());
        user.setFullName(armsUser.firstName() + " " + armsUser.lastName());
        user.setProfileImg(armsUser.profileImage());
        if (user.getStatus() == null) {
            user.setStatus(true);
        }

        userRepository.save(user);

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
