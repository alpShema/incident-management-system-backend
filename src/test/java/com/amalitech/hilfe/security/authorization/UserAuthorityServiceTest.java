package com.amalitech.hilfe.security.authorization;

import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Permission;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.RolePermission;
import com.amalitech.hilfe.models.User;
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

    UserAuthorityService userAuthorityService;

    @BeforeEach
    void setUp() {
        userAuthorityService = new UserAuthorityService(userRepository, rolePermissionRepository, "super-admin-id");
    }

    @Test
    void resolve_explicitRoleIncludesRoleAuthorityAndRolePermissions() {
        User user = User.builder()
            .id("u1")
            .email("admin@test.com")
            .roleCode(RoleCode.ADMIN)
            .permissions(List.of("create-agent"))
            .build();
        Permission permission = Permission.builder().id(1L).code("agent.read").name("Agent Read").build();
        RolePermission rolePermission = RolePermission.builder()
            .id(1L)
            .roleCode(RoleCode.ADMIN)
            .permission(permission)
            .build();

        when(rolePermissionRepository.findAllByRoleCode(RoleCode.ADMIN)).thenReturn(List.of(rolePermission));

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.roleCode()).isEqualTo(RoleCode.ADMIN);
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

        when(rolePermissionRepository.findAllByRoleCode(RoleCode.ADMIN)).thenReturn(List.of());

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.roleCode()).isEqualTo(RoleCode.ADMIN);
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

        when(rolePermissionRepository.findAllByRoleCode(RoleCode.AGENT)).thenReturn(List.of());

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.roleCode()).isEqualTo(RoleCode.AGENT);
        assertThat(resolvedAuthorities.authorities())
            .extracting(Object::toString)
            .contains("ROLE_AGENT");
    }

    @Test
    void resolve_bootstrapSuperAdminOverridesRoleCode() {
        User user = User.builder()
            .id("super-admin-id")
            .email("super-admin@test.com")
            .roleCode(RoleCode.CLIENT)
            .build();

        when(rolePermissionRepository.findAllByRoleCode(RoleCode.SUPER_ADMIN)).thenReturn(List.of());

        UserAuthorityService.ResolvedAuthorities resolvedAuthorities = userAuthorityService.resolve(user);

        assertThat(resolvedAuthorities.roleCode()).isEqualTo(RoleCode.SUPER_ADMIN);
        assertThat(resolvedAuthorities.authorities())
            .extracting(Object::toString)
            .contains("ROLE_SUPER_ADMIN");
    }
}
