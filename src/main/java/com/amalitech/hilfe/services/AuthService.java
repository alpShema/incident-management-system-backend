package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.RefreshTokenRequest;
import com.amalitech.hilfe.dto.TokenResponse;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final ArmsClient armsClient;
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

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(tokenService.getAccessTokenTtlSeconds())
                .refreshTokenExpiresIn(tokenService.getRefreshTokenTtlSeconds())
                .build();
    }

    public TokenResponse refresh(RefreshTokenRequest request) {
        throw new UnsupportedOperationException("Refresh token flow not implemented yet");
    }

    public void logout(RefreshTokenRequest request) {
        // TODO: Revoke refresh token when storage is implemented.
    }

}
