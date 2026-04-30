package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.RefreshTokenRequest;
import com.amalitech.hilfe.dto.TokenResponse;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final ArmsClient armsClient;
    private final TokenService tokenService;
    private final UserRepository userRepository;
    @Transactional
    public TokenResponse login(LoginRequest request) {
        logDecodedArmsToken(request.armsToken());

        ArmsUserInfo armsUser = armsClient.getUserByToken(request.armsToken());

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

    public TokenResponse refresh(RefreshTokenRequest request) {
        throw new UnsupportedOperationException("Refresh token flow not implemented yet");
    }

    public void logout(RefreshTokenRequest request) {
        // TODO: Revoke refresh token when storage is implemented.
    }

    private void logDecodedArmsToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                log.warn("ARMS token does not look like a JWT (missing payload part)");
                return;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            log.debug("Decoded ARMS token payload: {}", payload);
        } catch (Exception e) {
            log.warn("Could not decode ARMS token payload: {}", e.getMessage());
        }
    }
}
