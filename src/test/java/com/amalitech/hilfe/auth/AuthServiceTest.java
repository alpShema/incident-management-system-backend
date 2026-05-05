package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.RefreshTokenRequest;
import com.amalitech.hilfe.dto.TokenResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.ArmsClient;
import com.amalitech.hilfe.services.AuthService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock ArmsClient armsClient;
    @Mock TokenService tokenService;
    @Mock UserRepository userRepository;
    @InjectMocks AuthService authService;

    @Test
    void login_happyPath_upsertsUserAndReturnsTokens() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", "http://img.png");
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").build();

        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(user)).thenReturn("access-jwt");
        when(tokenService.generateRefreshToken(user)).thenReturn("refresh-jwt");
        when(tokenService.getAccessTokenTtlSeconds()).thenReturn(3600L);
        when(tokenService.getRefreshTokenTtlSeconds()).thenReturn(86400L);

        TokenResponse result = authService.login(new LoginRequest("arms-token"));

        assertThat(result.getAccessToken()).isEqualTo("access-jwt");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-jwt");
        assertThat(result.getAccessTokenExpiresIn()).isEqualTo(3600L);
        assertThat(result.getRefreshTokenExpiresIn()).isEqualTo(86400L);
        verify(userRepository).upsert("u1", "john@test.com", "John Doe", "http://img.png");
    }

    @Test
    void login_concatenatesFirstAndLastName() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u2", "Alice", "Smith", "alice@test.com", null);
        User user = User.builder().id("u2").email("alice@test.com").fullName("Alice Smith").build();

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(userRepository.findAuthUserById("u2")).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(any())).thenReturn("at");
        when(tokenService.generateRefreshToken(any())).thenReturn("rt");

        authService.login(new LoginRequest("token"));

        verify(userRepository).upsert("u2", "alice@test.com", "Alice Smith", null);
    }

    @Test
    void login_armsClientThrows_propagatesArmsAuthException() {
        when(armsClient.getUserByToken(any())).thenThrow(new ArmsAuthException("Invalid token", 401));

        assertThatThrownBy(() -> authService.login(new LoginRequest("bad-token")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Invalid token");
    }

    @Test
    void login_userNotFoundAfterUpsert_throwsIllegalState() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", null);

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("token")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("u1");
    }

    @Test
    void refresh_throwsUnsupportedOperation() {
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("rt")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void logout_completesWithoutException() {
        assertThatCode(() -> authService.logout(new RefreshTokenRequest("rt")))
                .doesNotThrowAnyException();
    }
}
