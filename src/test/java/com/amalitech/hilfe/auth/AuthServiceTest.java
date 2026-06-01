package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.dto.AuthResult;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.UserPermissionsResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Location;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.LocationRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import com.amalitech.hilfe.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock ArmsClient armsClient;
    @Mock ArmsTokenExpiryService armsTokenExpiryService;
    @Mock TokenService tokenService;
    @Mock TokenRevocationService tokenRevocationService;
    @Mock UserRepository userRepository;
    @Mock LocationRepository locationRepository;
    @Mock UserAuthorityService userAuthorityService;
    @InjectMocks AuthService authService;

    private static final String TEST_JTI = "test-jti-1";
    private static final Instant TEST_EXPIRY = Instant.now().plusSeconds(3600);

    @Test
    void login_happyPath_upsertsUserAndReturnsTokens() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", "http://img.png", null);
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").roleCode(RoleCode.CLIENT).build();

        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);
        when(armsTokenExpiryService.getRemainingLifetimeSeconds("arms-token")).thenReturn(7200L);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(user)).thenReturn("access-jwt");
        when(tokenService.generateRefreshToken(user, 7200L)).thenReturn("refresh-jwt");
        when(tokenService.getAccessTokenTtlSeconds()).thenReturn(3600L);

        AuthResult result = authService.login(new LoginRequest("arms-token"));

        assertThat(result.tokens().getAccessToken()).isEqualTo("access-jwt");
        assertThat(result.tokens().getRefreshToken()).isEqualTo("refresh-jwt");
        assertThat(result.tokens().getAccessTokenExpiresIn()).isEqualTo(3600L);
        assertThat(result.tokens().getRefreshTokenExpiresIn()).isEqualTo(7200L);
        assertThat(result.session().getUserId()).isEqualTo("u1");
        assertThat(result.session().getEmail()).isEqualTo("john@test.com");
        verify(userRepository).upsert("u1", "john@test.com", "John Doe", null, "http://img.png", null, null);
    }

    @Test
    void login_concatenatesFirstAndLastName() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u2", "Alice", "Smith", "alice@test.com", null, null);
        User user = User.builder().id("u2").email("alice@test.com").fullName("Alice Smith").roleCode(RoleCode.CLIENT).build();

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(armsTokenExpiryService.getRemainingLifetimeSeconds("token")).thenReturn(5400L);
        when(userRepository.findAuthUserById("u2")).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(any())).thenReturn("at");
        when(tokenService.generateRefreshToken(any(), anyLong())).thenReturn("rt");

        authService.login(new LoginRequest("token"));

        verify(userRepository).upsert("u2", "alice@test.com", "Alice Smith", null, null, null, null);
    }

    @Test
    void login_mapsContactPositionAndNormalizedOfficeToExistingColumns() {
        ArmsUserInfo armsUser = new ArmsUserInfo(
                "1170",
                "Sofia",
                "Patel",
                null,
                "sofia.patel@amalitech.com",
                "https://example.com/profile.webp",
                "Head of Department Level 1",
                "Takoradi Office",
                "sofia.patel@amalitech.com",
                "sofia.patel@amalitech.com",
                "23305578708"
        );
        Location location = Location.builder().id("loc-takoradi").name("Takoradi").build();
        User user = User.builder()
                .id("1170")
                .email("sofia.patel@amalitech.com")
                .fullName("Sofia Patel")
                .roleCode(RoleCode.CLIENT)
                .build();

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(locationRepository.findByNameIgnoreCase("Takoradi Office")).thenReturn(Optional.empty());
        when(locationRepository.findByNameIgnoreCase("Takoradi")).thenReturn(Optional.of(location));
        when(armsTokenExpiryService.getRemainingLifetimeSeconds("token")).thenReturn(5400L);
        when(userRepository.findAuthUserById("1170")).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(any())).thenReturn("at");
        when(tokenService.generateRefreshToken(any(), anyLong())).thenReturn("rt");

        authService.login(new LoginRequest("token"));

        verify(userRepository).upsert(
                "1170",
                "sofia.patel@amalitech.com",
                "Sofia Patel",
                "23305578708",
                "https://example.com/profile.webp",
                "Head of Department Level 1",
                "loc-takoradi"
        );
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
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", null, null);

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("token")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("u1");
    }

    @Test
    void login_firstLoginPersistsClientRole() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", null, null);
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").roleCode(RoleCode.CLIENT).build();

        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);
        when(armsTokenExpiryService.getRemainingLifetimeSeconds("arms-token")).thenReturn(7200L);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(user)).thenReturn("access-jwt");
        when(tokenService.generateRefreshToken(user, 7200L)).thenReturn("refresh-jwt");
        when(tokenService.getAccessTokenTtlSeconds()).thenReturn(3600L);

        AuthResult result = authService.login(new LoginRequest("arms-token"));

        assertThat(result.session().getUserId()).isEqualTo("u1");
        assertThat(user.getRoleCode()).isEqualTo("CLIENT");
        verify(userRepository).upsert("u1", "john@test.com", "John Doe", null, null, null, null);
    }

    @Test
    void refresh_happyPath_reissuesTokensUsingArmsExpiry() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", "http://img.png", null);
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").build();

        when(tokenService.authenticateRefreshToken("rt"))
                .thenReturn(Optional.of(new TokenService.RefreshPrincipal("u1", "john@test.com", TEST_JTI, TEST_EXPIRY)));
        when(tokenRevocationService.isRevoked(TEST_JTI)).thenReturn(false);
        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);
        when(armsTokenExpiryService.getRemainingLifetimeSeconds("arms-token")).thenReturn(1800L);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(user)).thenReturn("new-access");
        when(tokenService.generateRefreshToken(user, 1800L)).thenReturn("new-refresh");
        when(tokenService.getAccessTokenTtlSeconds()).thenReturn(3600L);

        AuthResult result = authService.refresh("rt", "arms-token");

        assertThat(result.tokens().getAccessToken()).isEqualTo("new-access");
        assertThat(result.tokens().getRefreshToken()).isEqualTo("new-refresh");
        assertThat(result.tokens().getAccessTokenExpiresIn()).isEqualTo(3600L);
        assertThat(result.tokens().getRefreshTokenExpiresIn()).isEqualTo(1800L);
        assertThat(result.session().getUserId()).isEqualTo("u1");
        verify(userRepository).upsert("u1", "john@test.com", "John Doe", null, "http://img.png", null, null);
    }

    @Test
    void refresh_successfulRefresh_revokesOldJti() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", "http://img.png", null);
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").build();

        when(tokenService.authenticateRefreshToken("rt"))
                .thenReturn(Optional.of(new TokenService.RefreshPrincipal("u1", "john@test.com", TEST_JTI, TEST_EXPIRY)));
        when(tokenRevocationService.isRevoked(TEST_JTI)).thenReturn(false);
        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);
        when(armsTokenExpiryService.getRemainingLifetimeSeconds("arms-token")).thenReturn(1800L);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(user)).thenReturn("new-access");
        when(tokenService.generateRefreshToken(user, 1800L)).thenReturn("new-refresh");
        when(tokenService.getAccessTokenTtlSeconds()).thenReturn(3600L);

        authService.refresh("rt", "arms-token");

        verify(tokenRevocationService).revoke(TEST_JTI, "u1", TEST_EXPIRY);
    }

    @Test
    void refresh_revokedToken_throwsUnauthorized() {
        when(tokenService.authenticateRefreshToken("rt"))
                .thenReturn(Optional.of(new TokenService.RefreshPrincipal("u1", "john@test.com", TEST_JTI, TEST_EXPIRY)));
        when(tokenRevocationService.isRevoked(TEST_JTI)).thenReturn(true);

        assertThatThrownBy(() -> authService.refresh("rt", "arms-token"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Refresh token has been revoked");
    }

    @Test
    void refresh_legacyTokenWithNullJti_throwsUnauthorized() {
        when(tokenService.authenticateRefreshToken("rt"))
                .thenReturn(Optional.of(new TokenService.RefreshPrincipal("u1", "john@test.com", null, TEST_EXPIRY)));
        when(tokenRevocationService.isRevoked(null)).thenReturn(true);

        assertThatThrownBy(() -> authService.refresh("rt", "arms-token"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Refresh token has been revoked");
    }

    @Test
    void refresh_invalidRefreshToken_throwsUnauthorized() {
        when(tokenService.authenticateRefreshToken("rt")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("rt", "arms-token"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void refresh_mismatchedArmsUser_throwsUnauthorized() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u2", "John", "Doe", "john@test.com", null, null);

        when(tokenService.authenticateRefreshToken("rt"))
                .thenReturn(Optional.of(new TokenService.RefreshPrincipal("u1", "john@test.com", TEST_JTI, TEST_EXPIRY)));
        when(tokenRevocationService.isRevoked(TEST_JTI)).thenReturn(false);
        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);

        assertThatThrownBy(() -> authService.refresh("rt", "arms-token"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Refresh token does not match the authenticated ARMS user");
    }

    @Test
    void logout_nullToken_completesWithoutException() {
        assertThatCode(() -> authService.logout(null))
                .doesNotThrowAnyException();
        verify(tokenRevocationService, never()).revoke(any(), any(), any());
    }

    @Test
    void logout_withValidRefreshToken_revokesJti() {
        when(tokenService.authenticateRefreshToken("rt"))
                .thenReturn(Optional.of(new TokenService.RefreshPrincipal("u1", "john@test.com", TEST_JTI, TEST_EXPIRY)));

        authService.logout("rt");

        verify(tokenRevocationService).revoke(TEST_JTI, "u1", TEST_EXPIRY);
        verify(userRepository).incrementTokenVersion("u1");
    }

    @Test
    void logout_withValidRefreshToken_incrementsTokenVersion() {
        when(tokenService.authenticateRefreshToken("rt"))
                .thenReturn(Optional.of(new TokenService.RefreshPrincipal("u1", "john@test.com", TEST_JTI, TEST_EXPIRY)));

        authService.logout("rt");

        verify(userRepository).incrementTokenVersion("u1");
    }

    @Test
    void logout_withInvalidRefreshToken_skipsRevocation() {
        when(tokenService.authenticateRefreshToken("bad-token")).thenReturn(Optional.empty());

        assertThatCode(() -> authService.logout("bad-token"))
                .doesNotThrowAnyException();
        verify(tokenRevocationService, never()).revoke(any(), any(), any());
    }

    @Test
    void getUserPermissions_returnsNonRoleAuthorities() {
        when(userAuthorityService.resolveByUserId("u1")).thenReturn(Optional.of(
                new UserAuthorityService.ResolvedAuthorities(
                        "u1",
                        "john@test.com",
                        RoleCode.CLIENT,
                        List.of(
                                () -> "ROLE_CLIENT",
                                () -> "incident.create",
                                () -> "incident.read.own"
                        ),
                        1
                )
        ));

        UserPermissionsResponse response = authService.getUserPermissions("u1");

        assertThat(response.getUserId()).isEqualTo("u1");
        assertThat(response.getPermissions()).containsExactly("incident.create", "incident.read.own");
    }
}
