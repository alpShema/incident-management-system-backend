package com.amalitech.hilfe;

import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.AdminService;
import com.amalitech.hilfe.services.IncidentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock UserRepository userRepository;
    @Mock AgentRepository agentRepository;
    @Mock IncidentService incidentService;
    @InjectMocks AdminService adminService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User adminUser() {
        return User.builder()
                .id("user-1")
                .email("admin@test.com")
                .fullName("Admin User")
                .roleCode(RoleCode.ADMIN)
                .build();
    }

    private User clientUser() {
        return User.builder()
                .id("user-2")
                .email("client@test.com")
                .fullName("Client User")
                .roleCode(RoleCode.CLIENT)
                .build();
    }

    private Agent agentWithUser(boolean status) {
        Agent a = Agent.builder()
                .id("agent-1")
                .userId("user-1")
                .status(status)
                .build();
        a.setUser(adminUser());
        return a;
    }

    // ── grantAgentAccess ──────────────────────────────────────────────────────

    @Test
    void grantAgentAccess_userNotFound_throws404() {
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.grantAgentAccess("user-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void grantAgentAccess_nonAdminUser_throws400() {
        when(userRepository.findById("user-2")).thenReturn(Optional.of(clientUser()));

        assertThatThrownBy(() -> adminService.grantAgentAccess("user-2"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User is not an admin")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void grantAgentAccess_noExistingAgent_createsNewRecord() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminUser()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.of(agentWithUser(true)));

        var result = adminService.grantAgentAccess("user-1");

        var captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(captor.getValue().getStatus()).isTrue();
        assertThat(captor.getValue().getId()).isNotBlank();
        assertThat(result.status()).isTrue();
        assertThat(result.agentId()).isEqualTo("agent-1");
    }

    @Test
    void grantAgentAccess_existingInactiveAgent_reactivates() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminUser()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agentWithUser(false)));
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.of(agentWithUser(true)));

        var result = adminService.grantAgentAccess("user-1");

        var captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isTrue();
        assertThat(result.status()).isTrue();
    }

    @Test
    void grantAgentAccess_existingActiveAgent_isIdempotent() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminUser()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agentWithUser(true)));
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.of(agentWithUser(true)));

        var result = adminService.grantAgentAccess("user-1");

        verify(agentRepository, never()).save(any(Agent.class));
        assertThat(result.status()).isTrue();
    }

    @Test
    void grantAgentAccess_superAdminRole_isAllowed() {
        var superAdmin = User.builder().id("user-1").email("sa@test.com").roleCode(RoleCode.SUPER_ADMIN).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(superAdmin));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.of(agentWithUser(true)));

        var result = adminService.grantAgentAccess("user-1");

        verify(agentRepository).save(any(Agent.class));
        assertThat(result).isNotNull();
    }

    @Test
    void grantAgentAccess_adminAgentRole_isAllowed() {
        var adminAgent = User.builder().id("user-1").email("aa@test.com").fullName("Admin Agent User").roleCode(RoleCode.ADMIN_AGENT).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminAgent));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.of(agentWithUser(true)));

        var result = adminService.grantAgentAccess("user-1");

        verify(agentRepository).save(any(Agent.class));
        assertThat(result).isNotNull();
    }

    @Test
    void revokeAgentAccess_adminAgentRole_isAllowed() {
        var adminAgent = User.builder().id("user-1").email("aa@test.com").fullName("Admin Agent User").roleCode(RoleCode.ADMIN_AGENT).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminAgent));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agentWithUser(true)));
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.of(agentWithUser(false)));

        var result = adminService.revokeAgentAccess("user-1");

        var captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isFalse();
        assertThat(result.status()).isFalse();
        verify(incidentService).unassignAllForDeactivatedAgent("agent-1", null);
    }

    // ── revokeAgentAccess ─────────────────────────────────────────────────────

    @Test
    void revokeAgentAccess_userNotFound_throws404() {
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.revokeAgentAccess("user-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void revokeAgentAccess_nonAdminUser_throws400() {
        when(userRepository.findById("user-2")).thenReturn(Optional.of(clientUser()));

        assertThatThrownBy(() -> adminService.revokeAgentAccess("user-2"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User is not an admin")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void revokeAgentAccess_noAgentRecord_throws404() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminUser()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.revokeAgentAccess("user-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Admin has no agent access")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void revokeAgentAccess_activeAgent_setsStatusFalse() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminUser()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agentWithUser(true)));
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.of(agentWithUser(false)));

        var result = adminService.revokeAgentAccess("user-1");

        var captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isFalse();
        assertThat(result.status()).isFalse();
        verify(incidentService).unassignAllForDeactivatedAgent("agent-1", null);
    }

    @Test
    void revokeAgentAccess_alreadyRevoked_isIdempotent() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminUser()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agentWithUser(false)));
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.of(agentWithUser(false)));

        var result = adminService.revokeAgentAccess("user-1");

        verify(agentRepository, never()).save(any(Agent.class));
        assertThat(result.status()).isFalse();
        verify(incidentService, never()).unassignAllForDeactivatedAgent(any(), any());
    }

    // ── getAgentAccess ────────────────────────────────────────────────────────

    @Test
    void getAgentAccess_userNotFound_throws404() {
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getAgentAccess("user-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void getAgentAccess_nonAdminUser_throws400() {
        when(userRepository.findById("user-2")).thenReturn(Optional.of(clientUser()));

        assertThatThrownBy(() -> adminService.getAgentAccess("user-2"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("User is not an admin")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void getAgentAccess_noAgentRecord_throws404() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminUser()));
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getAgentAccess("user-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Admin has no agent access")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void getAgentAccess_found_returnsAgentResponse() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(adminUser()));
        when(agentRepository.findByUserIdWithUser("user-1")).thenReturn(Optional.of(agentWithUser(true)));

        var result = adminService.getAgentAccess("user-1");

        assertThat(result.agentId()).isEqualTo("agent-1");
        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.email()).isEqualTo("admin@test.com");
        assertThat(result.status()).isTrue();
    }
}
