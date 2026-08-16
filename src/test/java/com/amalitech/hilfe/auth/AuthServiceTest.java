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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock ArmsClient armsClient;
    @Mock TokenService tokenService;
    @Mock TokenRevocationService tokenRevocationService;
    @Mock UserRepository userRepository;
    @Mock LocationRepository locationRepository;
    @Mock UserAuthorityService userAuthorityService;
    @InjectMocks AuthService authService;

    @Test
    void login_happyPath_upsertsUserAndReturnsSessionTtl() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", "http://img.png", null);
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").roleCode(RoleCode.CLIENT).build();

        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);
        when(tokenService.getArmsTokenRemainingSeconds("arms-token")).thenReturn(7200L);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.of(user));

        AuthResult result = authService.login(new LoginRequest("arms-token"));

        assertThat(result.sessionTtlSeconds()).isEqualTo(7200L);
        assertThat(result.session().getUserId()).isEqualTo("u1");
        assertThat(result.session().getEmail()).isEqualTo("john@test.com");
        verify(userRepository).upsert("u1", "john@test.com", "John Doe", null, "http://img.png", null, null);
    }

    @Test
    void login_concatenatesFirstAndLastName() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u2", "Alice", "Smith", "alice@test.com", null, null);
        User user = User.builder().id("u2").email("alice@test.com").fullName("Alice Smith").roleCode(RoleCode.CLIENT).build();

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(tokenService.getArmsTokenRemainingSeconds("token")).thenReturn(5400L);
        when(userRepository.findAuthUserById("u2")).thenReturn(Optional.of(user));

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
        when(tokenService.getArmsTokenRemainingSeconds("token")).thenReturn(5400L);
        when(userRepository.findAuthUserById("1170")).thenReturn(Optional.of(user));

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
    void login_deactivatedUser_throws403() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", null, null);
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").status(false).build();

        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.of(user));

        LoginRequest loginRequest = new LoginRequest("arms-token");
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("deactivated")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void login_armsClientThrows_propagatesArmsAuthException() {
        when(armsClient.getUserByToken(any())).thenThrow(new ArmsAuthException("Invalid token", 401));

        LoginRequest badLoginRequest = new LoginRequest("bad-token");
        assertThatThrownBy(() -> authService.login(badLoginRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Invalid token");
    }

    @Test
    void login_userNotFoundAfterUpsert_throwsIllegalState() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", null, null);

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.empty());

        LoginRequest tokenRequest = new LoginRequest("token");
        assertThatThrownBy(() -> authService.login(tokenRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("u1");
    }

    @Test
    void login_firstLoginPersistsClientRole() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", null, null);
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").roleCode(RoleCode.CLIENT).build();

        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);
        when(tokenService.getArmsTokenRemainingSeconds("arms-token")).thenReturn(7200L);
        when(userRepository.findAuthUserById("u1")).thenReturn(Optional.of(user));

        AuthResult result = authService.login(new LoginRequest("arms-token"));

        assertThat(result.session().getUserId()).isEqualTo("u1");
        assertThat(user.getRoleCode()).isEqualTo("CLIENT");
        verify(userRepository).upsert("u1", "john@test.com", "John Doe", null, null, null, null);
    }

    @Test
    void logout_nullToken_completesWithoutException() {
        assertThatCode(() -> authService.logout(null))
                .doesNotThrowAnyException();
        verify(tokenRevocationService, never()).revoke(any(), any(), any());
    }

    @Test
    void logout_blankToken_completesWithoutException() {
        assertThatCode(() -> authService.logout(" "))
                .doesNotThrowAnyException();
        verify(tokenRevocationService, never()).revoke(any(), any(), any());
    }

    @Test
    void logout_withValidSessionToken_revokesToken() {
        JwtTokenService.AuthPrincipal principal = new JwtTokenService.AuthPrincipal("u1", "john@test.com", "CLIENT");
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        when(tokenService.authenticateAccessToken("arms-token")).thenReturn(Optional.of(auth));
        when(tokenService.getArmsTokenRemainingSeconds("arms-token")).thenReturn(3600L);

        authService.logout("arms-token");

        verify(tokenRevocationService).revoke(eq("arms-token"), eq("u1"), any(Instant.class));
    }

    @Test
    void logout_withInvalidSessionToken_skipsRevocation() {
        when(tokenService.authenticateAccessToken("bad-token")).thenReturn(Optional.empty());

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
