package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.*;
import com.amalitech.hilfe.repositories.*;
import com.amalitech.hilfe.services.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock PermissionRepository permissionRepository;
    @Mock RolePermissionRepository rolePermissionRepository;
    @Mock UserRepository userRepository;
    @Mock AgentRepository agentRepository;
    @InjectMocks RoleService roleService;

    private Permission perm(String code) {
        Permission p = new Permission();
        p.setCode(code);
        p.setName(code);
        return p;
    }

    private Role role(String id, String code, String name) {
        return Role.builder().id(id).code(code).name(name).description("desc").systemDefined(false).build();
    }

    // ── createRole ────────────────────────────────────────────────────────────

    @Test
    void createRole_success_returnsRoleWithCode() {
        when(roleRepository.existsByCode("CUSTOM_ROLE")).thenReturn(false);
        when(roleRepository.existsByNameIgnoreCase("Custom Role")).thenReturn(false);
        Permission p = perm("incident.create");
        when(permissionRepository.findByCodeIn(List.of("incident.create"))).thenReturn(List.of(p));
        Role saved = role("r1", "CUSTOM_ROLE", "Custom Role");
        when(roleRepository.save(any(Role.class))).thenReturn(saved);
        when(rolePermissionRepository.saveAll(anyList())).thenReturn(List.of());

        RoleResponse response = roleService.createRole(new CreateRoleRequest("Custom Role", "desc", List.of("incident.create")));

        assertThat(response.roleCode()).isEqualTo("CUSTOM_ROLE");
        assertThat(response.name()).isEqualTo("Custom Role");
    }

    @Test
    void createRole_protectedRoleCode_throws409() {
        var request = new CreateRoleRequest("Admin", "d", List.of("incident.create"));
        assertThatThrownBy(() -> roleService.createRole(request))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void createRole_existingCodeInRepository_throws409() {
        when(roleRepository.existsByCode("SALES")).thenReturn(true);

        var request = new CreateRoleRequest("Sales", "d", List.of("incident.create"));
        assertThatThrownBy(() -> roleService.createRole(request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Role already exists")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void createRole_existingRoleName_throws409() {
        when(roleRepository.existsByCode("SALES_TEAM")).thenReturn(false);
        when(roleRepository.existsByNameIgnoreCase("Sales Team")).thenReturn(true);

        var request = new CreateRoleRequest("Sales Team", "d", List.of("incident.create"));
        assertThatThrownBy(() -> roleService.createRole(request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Role name already exists")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void createRole_invalidPermissionCodes_throws400() {
        when(roleRepository.existsByCode("CUSTOM")).thenReturn(false);
        when(roleRepository.existsByNameIgnoreCase("Custom")).thenReturn(false);
        when(permissionRepository.findByCodeIn(anyList())).thenReturn(List.of()); // 0 found, 1 requested

        var request = new CreateRoleRequest("Custom", "d", List.of("bad.perm"));
        assertThatThrownBy(() -> roleService.createRole(request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("invalid")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void createRole_normalisesPermissionCodes() {
        when(roleRepository.existsByCode("CUSTOM")).thenReturn(false);
        when(roleRepository.existsByNameIgnoreCase("Custom")).thenReturn(false);
        Permission p = perm("incident.create");
        when(permissionRepository.findByCodeIn(List.of("incident.create"))).thenReturn(List.of(p));
        when(roleRepository.save(any(Role.class))).thenReturn(role("r1", "CUSTOM", "Custom"));
        when(rolePermissionRepository.saveAll(anyList())).thenReturn(List.of());

        // Raw input has extra whitespace and uppercase — should be normalised to lowercase trimmed
        roleService.createRole(new CreateRoleRequest("Custom", "d", List.of("  INCIDENT.CREATE  ")));

        verify(permissionRepository).findByCodeIn(List.of("incident.create"));
    }

    // ── listRoles ─────────────────────────────────────────────────────────────

    @Test
    void listRoles_noQuery_passesNullPattern() {
        when(roleRepository.findAllFiltered(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(role("r1", "ADMIN", "Admin"))));
        when(rolePermissionRepository.findByRoleCodeWithPermission(any())).thenReturn(List.of());

        roleService.listRoles(null, Pageable.unpaged());

        verify(roleRepository).findAllFiltered(null, Pageable.unpaged());
    }

    @Test
    void listRoles_blankQuery_passesNullPattern() {
        when(roleRepository.findAllFiltered(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        roleService.listRoles("   ", Pageable.unpaged());

        verify(roleRepository).findAllFiltered(null, Pageable.unpaged());
    }

    @Test
    void listRoles_withQuery_buildsLikePattern() {
        when(roleRepository.findAllFiltered(eq("%sales%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        roleService.listRoles("sales", Pageable.unpaged());

        verify(roleRepository).findAllFiltered("%sales%", Pageable.unpaged());
    }

    @Test
    void listRoles_queryWithSpecialChars_escapesPercentUnderscoreAndBang() {
        when(roleRepository.findAllFiltered(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        roleService.listRoles("a%b_c!d", Pageable.unpaged());

        verify(roleRepository).findAllFiltered("%a!%b!_c!!d%", Pageable.unpaged());
    }

    // ── bulkAssignRole ────────────────────────────────────────────────────────

    @Test
    void bulkAssignRole_success_updatesUsersRoleCodeAndReturnsCount() {
        Role r = role("r1", "CUSTOM", "Custom");
        User user = User.builder().id("u1").fullName("Alice").build();
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(userRepository.findAllById(List.of("u1"))).thenReturn(List.of(user));
        when(userRepository.saveAll(anyList())).thenReturn(List.of(user));

        BulkAssignRoleResponse response = roleService.bulkAssignRole("CUSTOM", new BulkAssignRoleRequest(List.of("u1")));

        assertThat(response.roleCode()).isEqualTo("CUSTOM");
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(user.getRoleCode()).isEqualTo("CUSTOM");
        verify(agentRepository, never()).findByUserId(any());
    }

    @Test
    void bulkAssignRole_roleNotFound_throws404() {
        when(roleRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        var request = new BulkAssignRoleRequest(List.of("u1"));
        assertThatThrownBy(() -> roleService.bulkAssignRole("MISSING", request))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void bulkAssignRole_blankUserIds_throws400() {
        Role r = role("r1", "CUSTOM", "Custom");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));

        var request = new BulkAssignRoleRequest(List.of("  "));
        assertThatThrownBy(() -> roleService.bulkAssignRole("CUSTOM", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("must not be empty")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void bulkAssignRole_missingUsers_throws404ListingIds() {
        Role r = role("r1", "CUSTOM", "Custom");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(userRepository.findAllById(anyList())).thenReturn(List.of()); // none found

        var request = new BulkAssignRoleRequest(List.of("u1", "u2"));
        assertThatThrownBy(() -> roleService.bulkAssignRole("CUSTOM", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("u1")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void bulkAssignRole_agentRole_createsAgentRecordForNewAgent() {
        Role r = role("r1", "AGENT", "Agent");
        User user = User.builder().id("u1").fullName("Bob").build();
        when(roleRepository.findByCode("AGENT")).thenReturn(Optional.of(r));
        when(userRepository.findAllById(anyList())).thenReturn(List.of(user));
        when(userRepository.saveAll(anyList())).thenReturn(List.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        roleService.bulkAssignRole("AGENT", new BulkAssignRoleRequest(List.of("u1")));

        verify(agentRepository).save(argThat(a -> "u1".equals(a.getUserId()) && Boolean.TRUE.equals(a.getStatus())));
    }

    @Test
    void bulkAssignRole_agentRoleExistingAgent_doesNotCreateDuplicate() {
        Role r = role("r1", "AGENT", "Agent");
        User user = User.builder().id("u1").fullName("Bob").build();
        when(roleRepository.findByCode("AGENT")).thenReturn(Optional.of(r));
        when(userRepository.findAllById(anyList())).thenReturn(List.of(user));
        when(userRepository.saveAll(anyList())).thenReturn(List.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(Agent.builder().id("a1").build()));

        roleService.bulkAssignRole("AGENT", new BulkAssignRoleRequest(List.of("u1")));

        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void bulkAssignRole_roleCodeNormalisedToUppercase() {
        Role r = role("r1", "CUSTOM", "Custom");
        User user = User.builder().id("u1").build();
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(userRepository.findAllById(anyList())).thenReturn(List.of(user));
        when(userRepository.saveAll(anyList())).thenReturn(List.of(user));

        roleService.bulkAssignRole("custom", new BulkAssignRoleRequest(List.of("u1")));

        verify(roleRepository).findByCode("CUSTOM");
    }

    // ── updateRole ────────────────────────────────────────────────────────────

    @Test
    void updateRole_name_updatesName() {
        Role r = role("r1", "CUSTOM", "Old Name");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(roleRepository.existsByNameIgnoreCaseAndCodeNot("New Name", "CUSTOM")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenReturn(r);
        when(rolePermissionRepository.findByRoleCodeWithPermission("CUSTOM")).thenReturn(List.of());

        RoleResponse response = roleService.updateRole("CUSTOM", new UpdateRoleRequest("New Name", null, null));

        assertThat(response.name()).isEqualTo("New Name");
        verify(roleRepository).save(r);
    }

    @Test
    void updateRole_blankName_throws400() {
        Role r = role("r1", "CUSTOM", "Old Name");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        var request = new UpdateRoleRequest("   ", null, null);

        assertThatThrownBy(() -> roleService.updateRole("CUSTOM", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("must not be blank")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void updateRole_duplicateName_throws409() {
        Role r = role("r1", "CUSTOM", "Old Name");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(roleRepository.existsByNameIgnoreCaseAndCodeNot("Existing Name", "CUSTOM")).thenReturn(true);
        var request = new UpdateRoleRequest("Existing Name", null, null);

        assertThatThrownBy(() -> roleService.updateRole("CUSTOM", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Role name already exists")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void updateRole_clearDescription_setsNull() {
        Role r = role("r1", "CUSTOM", "Name");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(roleRepository.save(any(Role.class))).thenReturn(r);
        when(rolePermissionRepository.findByRoleCodeWithPermission("CUSTOM")).thenReturn(List.of());

        roleService.updateRole("CUSTOM", new UpdateRoleRequest(null, "", null));

        assertThat(r.getDescription()).isNull();
    }

    @Test
    void updateRole_replacePermissions_replacesLinks() {
        Role r = role("r1", "CUSTOM", "Name");
        Permission p = perm("incident.create");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(permissionRepository.findByCodeIn(List.of("incident.create"))).thenReturn(List.of(p));
        when(rolePermissionRepository.saveAll(anyList())).thenReturn(List.of());
        when(roleRepository.save(any(Role.class))).thenReturn(r);

        RoleResponse response = roleService.updateRole("CUSTOM", new UpdateRoleRequest(null, null, List.of("incident.create")));

        verify(rolePermissionRepository).deleteByRoleCode("CUSTOM");
        verify(rolePermissionRepository).saveAll(anyList());
        assertThat(response.permissions()).hasSize(1);
    }

    @Test
    void updateRole_emptyPermissionList_removesAllPermissions() {
        Role r = role("r1", "CUSTOM", "Name");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(permissionRepository.findByCodeIn(List.of())).thenReturn(List.of());
        when(rolePermissionRepository.saveAll(anyList())).thenReturn(List.of());
        when(roleRepository.save(any(Role.class))).thenReturn(r);

        RoleResponse response = roleService.updateRole("CUSTOM", new UpdateRoleRequest(null, null, List.of()));

        verify(rolePermissionRepository).deleteByRoleCode("CUSTOM");
        assertThat(response.permissions()).isEmpty();
    }

    @Test
    void updateRole_invalidPermissionCode_throws400AndRollsBack() {
        Role r = role("r1", "CUSTOM", "Name");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(permissionRepository.findByCodeIn(anyList())).thenReturn(List.of());
        var request = new UpdateRoleRequest(null, null, List.of("bad.code"));

        assertThatThrownBy(() -> roleService.updateRole("CUSTOM", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("invalid")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
        verify(rolePermissionRepository, never()).deleteByRoleCode(any());
    }

    @Test
    void updateRole_roleNotFound_throws404() {
        when(roleRepository.findByCode("MISSING")).thenReturn(Optional.empty());
        var request = new UpdateRoleRequest("Name", null, null);

        assertThatThrownBy(() -> roleService.updateRole("MISSING", request))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateRole_systemDefinedRole_throws403() {
        Role r = Role.builder().id("r1").code("ADMIN").name("Admin").systemDefined(true).build();
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(r));
        var request = new UpdateRoleRequest("New Admin", null, null);

        assertThatThrownBy(() -> roleService.updateRole("ADMIN", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("System-defined roles cannot be modified")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void updateRole_allFieldsNull_noChange() {
        Role r = role("r1", "CUSTOM", "Original");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(roleRepository.save(any(Role.class))).thenReturn(r);
        when(rolePermissionRepository.findByRoleCodeWithPermission("CUSTOM")).thenReturn(List.of());

        RoleResponse response = roleService.updateRole("CUSTOM", new UpdateRoleRequest(null, null, null));

        assertThat(response.name()).isEqualTo("Original");
        verify(roleRepository, never()).existsByNameIgnoreCaseAndCodeNot(any(), any());
        verify(rolePermissionRepository, never()).deleteByRoleCode(any());
    }

    // ── removeUsersFromRole ───────────────────────────────────────────────────

    @Test
    void removeUsersFromRole_success_clearsRoleCode() {
        Role r = role("r1", "CUSTOM", "Custom");
        User user = User.builder().id("u1").build();
        user.setRoleCode("CUSTOM");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(userRepository.findAllById(List.of("u1"))).thenReturn(List.of(user));
        when(userRepository.saveAll(anyList())).thenReturn(List.of(user));

        BulkAssignRoleResponse response = roleService.removeUsersFromRole("CUSTOM", new BulkAssignRoleRequest(List.of("u1")));

        assertThat(user.getRoleCode()).isNull();
        assertThat(response.roleCode()).isEqualTo("CUSTOM");
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.updatedUserIds()).containsExactly("u1");
    }

    @Test
    void removeUsersFromRole_roleNotFound_throws404() {
        when(roleRepository.findByCode("MISSING")).thenReturn(Optional.empty());
        var request = new BulkAssignRoleRequest(List.of("u1"));

        assertThatThrownBy(() -> roleService.removeUsersFromRole("MISSING", request))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void removeUsersFromRole_missingUsers_throws404AndRollsBack() {
        Role r = role("r1", "CUSTOM", "Custom");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(userRepository.findAllById(anyList())).thenReturn(List.of());
        var request = new BulkAssignRoleRequest(List.of("u1", "u2"));

        assertThatThrownBy(() -> roleService.removeUsersFromRole("CUSTOM", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("u1")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
        verify(userRepository, never()).saveAll(anyList());
    }

    @Test
    void removeUsersFromRole_emptyUserIds_throws400() {
        Role r = role("r1", "CUSTOM", "Custom");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        var request = new BulkAssignRoleRequest(List.of("  "));

        assertThatThrownBy(() -> roleService.removeUsersFromRole("CUSTOM", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("must not be empty")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void removeUsersFromRole_usersNotInRole_areSkipped() {
        Role r = role("r1", "CUSTOM", "Custom");
        User userInRole = User.builder().id("u1").build();
        userInRole.setRoleCode("CUSTOM");
        User userInOtherRole = User.builder().id("u2").build();
        userInOtherRole.setRoleCode("OTHER");
        when(roleRepository.findByCode("CUSTOM")).thenReturn(Optional.of(r));
        when(userRepository.findAllById(List.of("u1", "u2"))).thenReturn(List.of(userInRole, userInOtherRole));
        when(userRepository.saveAll(anyList())).thenReturn(List.of(userInRole));

        BulkAssignRoleResponse response = roleService.removeUsersFromRole("CUSTOM", new BulkAssignRoleRequest(List.of("u1", "u2")));

        assertThat(userInRole.getRoleCode()).isNull();
        assertThat(userInOtherRole.getRoleCode()).isEqualTo("OTHER");
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.updatedUserIds()).containsExactly("u1");
    }

    // ── permissionCatalog ─────────────────────────────────────────────────────

    @Test
    void permissionCatalog_groupsPermissionsIntoCorrectCategories() {
        List<Permission> permissions = List.of(
                perm("incident.create"), perm("incident.read.own"),
                perm("agent.create"), perm("agent-group.read"),
                perm("department.read"), perm("severity.create"),
                perm("location.update"), perm("system.config.read"),
                perm("rbac.role.read"),
                perm("dashboard.admin")
        );
        when(permissionRepository.findAllByOrderByCodeAsc()).thenReturn(permissions);

        PermissionCatalogResponse catalog = roleService.permissionCatalog();

        assertThat(catalog.incidentPermissions()).hasSize(2);
        assertThat(catalog.agentAndGroupPermissions()).hasSize(2);
        assertThat(catalog.settingsPermissions()).hasSize(4); // department + severity + location + system
        assertThat(catalog.userPermissions()).hasSize(1);
        assertThat(catalog.reportPermissions()).hasSize(1);
    }

    @Test
    void permissionCatalog_systemConfigPermission_includedInSettings() {
        when(permissionRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(perm("system.config.read")));

        PermissionCatalogResponse catalog = roleService.permissionCatalog();

        assertThat(catalog.settingsPermissions()).hasSize(1);
        assertThat(catalog.settingsPermissions().getFirst().code()).isEqualTo("system.config.read");
    }
}
