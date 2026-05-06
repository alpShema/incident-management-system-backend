package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.dto.AuthResult;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.UserPermissionsResponse;
import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.services.ArmsTokenExpiryService;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.ArmsClient;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.AuthService;
import com.amalitech.hilfe.services.TokenService;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock ArmsClient armsClient;
    @Mock ArmsTokenExpiryService armsTokenExpiryService;
    @Mock TokenService tokenService;
    @Mock UserRepository userRepository;
    @Mock UserAuthorityService userAuthorityService;
    @Mock ActivityLogService activityLogService;
    @InjectMocks AuthService authService;

    @Test
    void login_happyPath_upsertsUserAndReturnsTokens() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", "http://img.png");
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").build();

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
        verify(userRepository).upsert("u1", "john@test.com", "John Doe", "http://img.png");
    }

    @Test
    void login_concatenatesFirstAndLastName() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u2", "Alice", "Smith", "alice@test.com", null);
        User user = User.builder().id("u2").email("alice@test.com").fullName("Alice Smith").build();

        when(armsClient.getUserByToken("token")).thenReturn(armsUser);
        when(armsTokenExpiryService.getRemainingLifetimeSeconds("token")).thenReturn(5400L);
        when(userRepository.findAuthUserById("u2")).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(any())).thenReturn("at");
        when(tokenService.generateRefreshToken(any(), anyLong())).thenReturn("rt");

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
    void refresh_happyPath_reissuesTokensUsingArmsExpiry() {
        ArmsUserInfo armsUser = new ArmsUserInfo("u1", "John", "Doe", "john@test.com", "http://img.png");
        User user = User.builder().id("u1").email("john@test.com").fullName("John Doe").build();

        when(tokenService.authenticateRefreshToken("rt"))
                .thenReturn(Optional.of(new TokenService.RefreshPrincipal("u1", "john@test.com")));
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
        verify(userRepository).upsert("u1", "john@test.com", "John Doe", "http://img.png");
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
        ArmsUserInfo armsUser = new ArmsUserInfo("u2", "John", "Doe", "john@test.com", null);

        when(tokenService.authenticateRefreshToken("rt"))
                .thenReturn(Optional.of(new TokenService.RefreshPrincipal("u1", "john@test.com")));
        when(armsClient.getUserByToken("arms-token")).thenReturn(armsUser);

        assertThatThrownBy(() -> authService.refresh("rt", "arms-token"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Refresh token does not match the authenticated ARMS user");
    }

    @Test
    void logout_completesWithoutException() {
        assertThatCode(() -> authService.logout())
                .doesNotThrowAnyException();
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
                        )
                )
        ));

        UserPermissionsResponse response = authService.getUserPermissions("u1");

        assertThat(response.getUserId()).isEqualTo("u1");
        assertThat(response.getPermissions()).containsExactly("incident.create", "incident.read.own");
    }

    @Test
    void getUserRoles_returnsPaginatedProjection() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<UserRoleSummaryResponse> page = new PageImpl<>(List.of(
                new UserRoleSummaryResponse("u1", "john@test.com", "John Doe", "http://img.png", RoleCode.ADMIN)
        ));
        when(userRepository.findUserRoleSummaries(pageable)).thenReturn(page);

        Page<UserRoleSummaryResponse> result = authService.getUserRoles(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().roleCode()).isEqualTo(RoleCode.ADMIN);
    }

    @Test
    void getActivityLogs_returnsPaginatedProjection() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<ActivityLogResponse> page = new PageImpl<>(List.of(
                new ActivityLogResponse(
                        1L,
                        "admin-1",
                        "u1",
                        "ROLE_CHANGED",
                        "USER",
                        "u1",
                        "Changed role for user u1 from CLIENT to ADMIN",
                        "{\"previousRoleCode\":\"CLIENT\",\"newRoleCode\":\"ADMIN\"}",
                        java.time.Instant.now()
                )
        ));
        when(activityLogService.getActivityLogs(pageable)).thenReturn(page);

        Page<ActivityLogResponse> result = authService.getActivityLogs(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().action()).isEqualTo("ROLE_CHANGED");
    }

    @Test
    void assignUserRole_updatesRoleAndReturnsSummary() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .profileImg("http://img.png")
                .roleCode(RoleCode.CLIENT)
                .build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        UserRoleSummaryResponse result = authService.assignUserRole("admin-1", "u1", RoleCode.ADMIN);

        assertThat(result.userId()).isEqualTo("u1");
        assertThat(result.roleCode()).isEqualTo(RoleCode.ADMIN);
        verify(activityLogService).logUserRoleChange("admin-1", "u1", RoleCode.CLIENT, RoleCode.ADMIN);
    }

    @Test
    void assignUserRole_userNotFound_throwsNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.assignUserRole("admin-1", "missing", RoleCode.ADMIN))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User not found");
    }

    @Test
    void assignUserRole_sameRole_doesNotLogActivity() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .roleCode(RoleCode.ADMIN)
                .build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        UserRoleSummaryResponse result = authService.assignUserRole("admin-1", "u1", RoleCode.ADMIN);

        assertThat(result.roleCode()).isEqualTo(RoleCode.ADMIN);
        verify(activityLogService, org.mockito.Mockito.never())
                .logUserRoleChange(any(), any(), any(), any());
    }
}
