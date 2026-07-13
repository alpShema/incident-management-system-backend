package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Role;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.repositories.AdminRepository;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.RoleRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock AgentRepository agentRepository;
    @Mock AdminRepository adminRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock RoleRepository roleRepository;
    @Mock ActivityLogService activityLogService;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @InjectMocks UserService userService;

    private Role role(String code, String name) {
        return Role.builder().id("role-" + code).code(code).name(name).systemDefined(true).build();
    }

    @Test
    void getUsers_returnsPaginatedProjection() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<UserRoleSummaryResponse> page = new PageImpl<>(List.of(
                new UserRoleSummaryResponse("u1", "john@test.com", "John Doe", "http://img.png", RoleCode.ADMIN, "Admin", true, "Accra", 2L, 1L)
        ));
        when(userRepository.findUserRoleSummariesUnified(isNull(), isNull(), isNull(), isNull(), eq(pageable))).thenReturn(page);

        Page<UserRoleSummaryResponse> result = userService.getUsers(null, null, null, (Boolean) null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().roleCode()).isEqualTo("ADMIN");
    }

    @Test
    void getUsers_activeStatus_passesTrue() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<UserRoleSummaryResponse> page = new PageImpl<>(List.of());
        when(userRepository.findUserRoleSummariesUnified(isNull(), isNull(), isNull(), eq(true), eq(pageable))).thenReturn(page);

        userService.getUsers(null, null, null, true, pageable);

        verify(userRepository).findUserRoleSummariesUnified(null, null, null, true, pageable);
    }

    @Test
    void getUsers_inactiveStatus_passesFalse() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<UserRoleSummaryResponse> page = new PageImpl<>(List.of());
        when(userRepository.findUserRoleSummariesUnified(isNull(), isNull(), isNull(), eq(false), eq(pageable))).thenReturn(page);

        userService.getUsers(null, null, null, false, pageable);

        verify(userRepository).findUserRoleSummariesUnified(null, null, null, false, pageable);
    }

    @Test
    void getUsers_locationId_passedThrough() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<UserRoleSummaryResponse> page = new PageImpl<>(List.of());
        when(userRepository.findUserRoleSummariesUnified(isNull(), isNull(), eq("LOC-ACCRA"), isNull(), eq(pageable))).thenReturn(page);

        userService.getUsers(null, null, "LOC-ACCRA", (Boolean) null, pageable);

        verify(userRepository).findUserRoleSummariesUnified(null, null, "LOC-ACCRA", null, pageable);
    }

    // -------------------------------------------------------------------------
    // updateUserStatus
    // -------------------------------------------------------------------------

    @Test
    void updateUserStatus_deactivateAgentUser_setsAgentStatusFalse() {
        User user = User.builder().id("u1").email("a@test.com").fullName("Agent One").status(true).build();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(true).build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));
        when(agentRepository.save(agent)).thenReturn(agent);

        userService.updateUserStatus("admin-1", RoleCode.ADMIN, "u1", false);

        assertThat(user.getStatus()).isFalse();
        assertThat(agent.getStatus()).isFalse();
        verify(userRepository).save(user);
        verify(agentRepository).save(agent);
    }

    @Test
    void updateUserStatus_reactivateAgentUser_setsAgentStatusTrue() {
        User user = User.builder().id("u1").email("a@test.com").fullName("Agent One").status(false).build();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(false).build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));
        when(agentRepository.save(agent)).thenReturn(agent);

        userService.updateUserStatus("admin-1", RoleCode.ADMIN, "u1", true);

        assertThat(user.getStatus()).isTrue();
        assertThat(agent.getStatus()).isTrue();
        verify(userRepository).save(user);
        verify(agentRepository).save(agent);
    }

    @Test
    void updateUserStatus_deactivateNonAgentUser_noAgentSave() {
        User user = User.builder().id("u1").email("a@test.com").fullName("Admin One").status(true).build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());

        userService.updateUserStatus("admin-1", RoleCode.ADMIN, "u1", false);

        assertThat(user.getStatus()).isFalse();
        verify(userRepository).save(user);
        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void updateUserStatus_agentAlreadyUnavailable_deactivationSucceeds() {
        User user = User.builder().id("u1").email("a@test.com").fullName("Agent One").status(true).build();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(false).build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));
        when(agentRepository.save(agent)).thenReturn(agent);

        userService.updateUserStatus("admin-1", RoleCode.ADMIN, "u1", false);

        assertThat(user.getStatus()).isFalse();
        assertThat(agent.getStatus()).isFalse();
        verify(userRepository).save(user);
        verify(agentRepository).save(agent);
    }

    @Test
    void updateUserStatus_selfUpdate_succeeds() {
        User user = User.builder().id("u1").email("a@test.com").fullName("Agent One").status(true).build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());

        userService.updateUserStatus("u1", RoleCode.AGENT, "u1", false);

        assertThat(user.getStatus()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void updateUserStatus_nonAdminUpdatingOther_throws403() {
        assertThatThrownBy(() -> userService.updateUserStatus("u2", RoleCode.AGENT, "u1", false))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("You can only update your own status")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateUserStatus_userNotFound_throws404() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserStatus("admin-1", RoleCode.ADMIN, "missing", false))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // -------------------------------------------------------------------------
    // assignUserRole
    // -------------------------------------------------------------------------

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
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(role("ADMIN", "Admin")));
        when(incidentRepository.countByUserId("u1")).thenReturn(3L);
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.ADMIN);

        assertThat(result.userId()).isEqualTo("u1");
        assertThat(result.roleCode()).isEqualTo("ADMIN");
        assertThat(result.roleName()).isEqualTo("Admin");
        verify(agentRepository, never()).save(any(Agent.class));
        verify(activityLogService).logUserRoleChange("admin-1", "u1", "CLIENT", "ADMIN");
    }

    @Test
    void assignUserRole_toAgent_createsAgentRecordWhenMissing() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .roleCode(RoleCode.CLIENT)
                .build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("AGENT")).thenReturn(Optional.of(role("AGENT", "Agent")));
        when(incidentRepository.countByUserId("u1")).thenReturn(2L);

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.AGENT);

        assertThat(result.roleCode()).isEqualTo("AGENT");
        var agentCaptor = forClass(Agent.class);
        verify(agentRepository).save(agentCaptor.capture());
        assertThat(agentCaptor.getValue().getUserId()).isEqualTo("u1");
        assertThat(agentCaptor.getValue().getStatus()).isTrue();
    }

    @Test
    void assignUserRole_toAgentWithExistingAgentRecord_doesNotCreateDuplicate() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .roleCode(RoleCode.CLIENT)
                .build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(
                Agent.builder().id("agent-1").userId("u1").status(true).build()));
        when(roleRepository.findByCode("AGENT")).thenReturn(Optional.of(role("AGENT", "Agent")));
        when(incidentRepository.countByUserId("u1")).thenReturn(4L);
        when(incidentRepository.countByAssignedToId("agent-1")).thenReturn(5L);

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.AGENT);

        assertThat(result.roleCode()).isEqualTo("AGENT");
        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void assignUserRole_userNotFound_throwsNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(role("ADMIN", "Admin")));

        assertThatThrownBy(() -> userService.assignUserRole("admin-1", "missing", RoleCode.ADMIN))
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
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(role("ADMIN", "Admin")));
        when(incidentRepository.countByUserId("u1")).thenReturn(1L);
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.ADMIN);

        assertThat(result.roleCode()).isEqualTo("ADMIN");
        verify(activityLogService, never())
                .logUserRoleChange(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void assignUserRole_demoteFromAgent_deactivatesAgentAndRemovesGroupMemberships() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .roleCode(RoleCode.AGENT)
                .build();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(true).build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(role("CLIENT", "Client")));
        when(incidentRepository.countByUserId("u1")).thenReturn(0L);
        when(incidentRepository.countByAssignedToId("agent-1")).thenReturn(0L);

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.CLIENT);

        assertThat(result.roleCode()).isEqualTo("CLIENT");
        assertThat(agent.getStatus()).isFalse();
        verify(agentRepository).save(agent);
        verify(agentGroupMemberRepository).deleteByAgentId("agent-1");
    }

    @Test
    void assignUserRole_repromoteToAgent_reactivatesExistingInactiveAgentRecord() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .roleCode(RoleCode.CLIENT)
                .build();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(false).build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));
        when(roleRepository.findByCode("AGENT")).thenReturn(Optional.of(role("AGENT", "Agent")));
        when(incidentRepository.countByUserId("u1")).thenReturn(0L);
        when(incidentRepository.countByAssignedToId("agent-1")).thenReturn(0L);

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.AGENT);

        assertThat(result.roleCode()).isEqualTo("AGENT");
        assertThat(agent.getStatus()).isTrue();
        verify(agentRepository).save(agent);
        verify(agentGroupMemberRepository, never()).deleteByAgentId(any());
    }

    @Test
    void assignUserRole_demoteFromAdmin_deactivatesAdminRecord() {
        User user = User.builder()
                .id("u1")
                .email("admin@test.com")
                .fullName("Admin One")
                .roleCode(RoleCode.ADMIN)
                .build();
        Admin admin = Admin.builder().id("admin-rec-1").userId("u1").status(true).build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(adminRepository.findByUserIdWithUser("u1")).thenReturn(Optional.of(admin));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(role("CLIENT", "Client")));
        when(incidentRepository.countByUserId("u1")).thenReturn(0L);

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.CLIENT);

        assertThat(result.roleCode()).isEqualTo("CLIENT");
        assertThat(admin.isStatus()).isFalse();
        verify(adminRepository).save(admin);
    }

    @Test
    void assignUserRole_repromoteToAdmin_reactivatesExistingInactiveAdminRecord() {
        User user = User.builder()
                .id("u1")
                .email("admin@test.com")
                .fullName("Admin One")
                .roleCode(RoleCode.CLIENT)
                .build();
        Admin admin = Admin.builder().id("admin-rec-1").userId("u1").status(false).build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(adminRepository.findByUserIdWithUser("u1")).thenReturn(Optional.of(admin));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(role("ADMIN", "Admin")));
        when(incidentRepository.countByUserId("u1")).thenReturn(0L);

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.ADMIN);

        assertThat(result.roleCode()).isEqualTo("ADMIN");
        assertThat(admin.isStatus()).isTrue();
        verify(adminRepository).save(admin);
    }

    @Test
    void assignUserRole_demoteWithNoExistingAgentOrAdminRecord_noSavesOrDeletes() {
        User user = User.builder()
                .id("u1")
                .email("john@test.com")
                .fullName("John Doe")
                .roleCode(RoleCode.AGENT)
                .build();

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(role("CLIENT", "Client")));
        when(incidentRepository.countByUserId("u1")).thenReturn(0L);

        UserRoleSummaryResponse result = userService.assignUserRole("admin-1", "u1", RoleCode.CLIENT);

        assertThat(result.roleCode()).isEqualTo("CLIENT");
        verify(agentRepository, never()).save(any(Agent.class));
        verify(agentGroupMemberRepository, never()).deleteByAgentId(any());
        verify(adminRepository, never()).save(any(Admin.class));
    }
}
