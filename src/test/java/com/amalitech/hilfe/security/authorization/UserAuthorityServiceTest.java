package com.amalitech.hilfe.security.authorization;

import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.RolePermissionRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthorityServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    RolePermissionRepository rolePermissionRepository;

    @Mock
    DepartmentRepository departmentRepository;

    UserAuthorityService userAuthorityService;

    @BeforeEach
    void setUp() {
        userAuthorityService = new UserAuthorityService(userRepository, rolePermissionRepository, departmentRepository, "super-admin-id");
    }

    @Test
    void resolve_explicitRoleIncludesRoleAuthorityAndRolePermissions() {
        User user = User.builder()
            .id("u1")
            .email("admin@test.com")
            .roleCode(RoleCode.ADMIN)
            .permissions(List.of("create-agent"))
            .build();

        when(rolePermissionRepository.findPermissionCodesByRoleCode("ADMIN"))
                .thenReturn(List.of("agent.read"));

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.roleCode()).isEqualTo("ADMIN");
        assertThat(resolvedAuthorities.authorities())
            .extracting(Object::toString)
            .contains("ROLE_ADMIN", "agent.read", "agent.create");
    }

    @Test
    void resolve_withoutRoleCodeFallsBackToLegacyAdmin() {
        User user = User.builder()
            .id("u2")
            .email("legacy-admin@test.com")
            .admin(Admin.builder().status(true).build())
            .build();

        when(rolePermissionRepository.findPermissionCodesByRoleCode("ADMIN")).thenReturn(List.of());

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.roleCode()).isEqualTo("ADMIN");
        assertThat(resolvedAuthorities.authorities())
            .extracting(Object::toString)
            .contains("ROLE_ADMIN");
    }

    @Test
    void resolve_withoutRoleCodeFallsBackToLegacyAgent() {
        User user = User.builder()
            .id("u3")
            .email("legacy-agent@test.com")
            .agent(Agent.builder().status(true).build())
            .build();

        when(rolePermissionRepository.findPermissionCodesByRoleCode("AGENT")).thenReturn(List.of());

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.roleCode()).isEqualTo("AGENT");
        assertThat(resolvedAuthorities.authorities())
            .extracting(Object::toString)
            .contains("ROLE_AGENT");
    }

    @Test
    void resolveByUserId_deactivatedUser_returnsEmpty() {
        User user = User.builder()
                .id("u-deactivated")
                .email("inactive@test.com")
                .roleCode(RoleCode.CLIENT)
                .status(false)
                .build();

        when(userRepository.findAuthUserById("u-deactivated")).thenReturn(java.util.Optional.of(user));

        assertThat(userAuthorityService.resolveByUserId("u-deactivated")).isEmpty();
    }

    @Test
    void resolveByUserId_activeUser_returnsAuthorities() {
        User user = User.builder()
                .id("u-active")
                .email("active@test.com")
                .roleCode(RoleCode.CLIENT)
                .status(true)
                .build();

        when(userRepository.findAuthUserById("u-active")).thenReturn(java.util.Optional.of(user));
        when(rolePermissionRepository.findPermissionCodesByRoleCode("CLIENT")).thenReturn(List.of());

        assertThat(userAuthorityService.resolveByUserId("u-active")).isPresent();
    }

    @Test
    void resolve_userHeadsADepartment_grantsDepartmentHeadAuthority() {
        User user = User.builder()
            .id("u-hod")
            .email("hod@test.com")
            .roleCode(RoleCode.ADMIN)
            .build();

        when(rolePermissionRepository.findPermissionCodesByRoleCode("ADMIN")).thenReturn(List.of());
        when(departmentRepository.existsByHeadUserId("u-hod")).thenReturn(true);

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.authorities())
            .extracting(Object::toString)
            .contains(RbacPermissions.DEPARTMENT_HEAD);
    }

    @Test
    void resolve_userDoesNotHeadADepartment_omitsDepartmentHeadAuthority() {
        User user = User.builder()
            .id("u-not-hod")
            .email("not-hod@test.com")
            .roleCode(RoleCode.ADMIN)
            .build();

        when(rolePermissionRepository.findPermissionCodesByRoleCode("ADMIN")).thenReturn(List.of());
        when(departmentRepository.existsByHeadUserId("u-not-hod")).thenReturn(false);

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.authorities())
            .extracting(Object::toString)
            .isNotEmpty()
            .doesNotContain(RbacPermissions.DEPARTMENT_HEAD);
    }

    @Test
    void resolve_bootstrapSuperAdminOverridesRoleCode() {
        User user = User.builder()
            .id("super-admin-id")
            .email("super-admin@test.com")
            .roleCode(RoleCode.CLIENT)
            .build();

        when(rolePermissionRepository.findPermissionCodesByRoleCode("SUPER_ADMIN")).thenReturn(List.of());

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.roleCode()).isEqualTo("SUPER_ADMIN");
        assertThat(resolvedAuthorities.authorities())
            .extracting(Object::toString)
            .contains("ROLE_SUPER_ADMIN");
    }
}
