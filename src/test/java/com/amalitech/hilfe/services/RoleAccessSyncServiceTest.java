package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AdminRepository;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAccessSyncServiceTest {

    @Mock AgentRepository agentRepository;
    @Mock AdminRepository adminRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @InjectMocks RoleAccessSyncService roleAccessSyncService;

    private User user() {
        return User.builder().id("u1").email("a@test.com").fullName("A User").build();
    }

    // ── syncAgentRecord ──────────────────────────────────────────────────────

    @Test
    void syncAgentRecord_promoteToAgent_createsAgentRecordWhenMissing() {
        User user = user();
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        roleAccessSyncService.syncAgentRecord(user, "AGENT");

        var captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("u1");
        assertThat(captor.getValue().getStatus()).isTrue();
    }

    @Test
    void syncAgentRecord_promoteToAdminAgent_createsAgentRecordWhenMissing() {
        User user = user();
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        roleAccessSyncService.syncAgentRecord(user, "ADMIN_AGENT");

        verify(agentRepository).save(any(Agent.class));
    }

    @Test
    void syncAgentRecord_promoteWithExistingActiveAgentRecord_doesNotCreateDuplicateOrSave() {
        User user = user();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(true).build();
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));

        roleAccessSyncService.syncAgentRecord(user, "AGENT");

        verify(agentRepository, never()).save(any(Agent.class));
    }

    @Test
    void syncAgentRecord_promoteWithExistingInactiveAgentRecord_reactivatesIt() {
        User user = user();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(false).build();
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));
        when(agentRepository.save(agent)).thenReturn(agent);

        roleAccessSyncService.syncAgentRecord(user, "AGENT");

        assertThat(agent.getStatus()).isTrue();
        verify(agentRepository).save(agent);
        verify(agentGroupMemberRepository, never()).deleteByAgentId(any());
    }

    @Test
    void syncAgentRecord_demoteFromAgent_deactivatesAgentAndRemovesGroupMemberships() {
        User user = user();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(true).build();
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));
        when(agentRepository.save(agent)).thenReturn(agent);

        roleAccessSyncService.syncAgentRecord(user, "CLIENT");

        assertThat(agent.getStatus()).isFalse();
        verify(agentRepository).save(agent);
        verify(agentGroupMemberRepository).deleteByAgentId("agent-1");
    }

    @Test
    void syncAgentRecord_demoteToNullRole_deactivatesAgentAndRemovesGroupMemberships() {
        User user = user();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(true).build();
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));
        when(agentRepository.save(agent)).thenReturn(agent);

        roleAccessSyncService.syncAgentRecord(user, null);

        assertThat(agent.getStatus()).isFalse();
        verify(agentGroupMemberRepository).deleteByAgentId("agent-1");
    }

    @Test
    void syncAgentRecord_demoteWithAlreadyInactiveAgentRecord_stillRemovesGroupMembershipsButNoSave() {
        User user = user();
        Agent agent = Agent.builder().id("agent-1").userId("u1").status(false).build();
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.of(agent));

        roleAccessSyncService.syncAgentRecord(user, "CLIENT");

        verify(agentRepository, never()).save(any(Agent.class));
        verify(agentGroupMemberRepository).deleteByAgentId("agent-1");
    }

    @Test
    void syncAgentRecord_demoteWithNoExistingAgentRecord_noSavesOrDeletes() {
        User user = user();
        when(agentRepository.findByUserId("u1")).thenReturn(Optional.empty());

        roleAccessSyncService.syncAgentRecord(user, "CLIENT");

        verify(agentRepository, never()).save(any(Agent.class));
        verify(agentGroupMemberRepository, never()).deleteByAgentId(any());
    }

    // ── syncAdminRecord ──────────────────────────────────────────────────────

    @Test
    void syncAdminRecord_promoteToAdmin_createsAdminRecordWhenMissing() {
        User user = user();
        when(adminRepository.findByUserIdWithUser("u1")).thenReturn(Optional.empty());
        when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        roleAccessSyncService.syncAdminRecord(user, "ADMIN");

        var captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("u1");
        assertThat(captor.getValue().isStatus()).isTrue();
    }

    @Test
    void syncAdminRecord_promoteWithExistingInactiveAdminRecord_reactivatesIt() {
        User user = user();
        Admin admin = Admin.builder().id("admin-1").userId("u1").status(false).build();
        when(adminRepository.findByUserIdWithUser("u1")).thenReturn(Optional.of(admin));
        when(adminRepository.save(admin)).thenReturn(admin);

        roleAccessSyncService.syncAdminRecord(user, "SUPER_ADMIN");

        assertThat(admin.isStatus()).isTrue();
        verify(adminRepository).save(admin);
    }

    @Test
    void syncAdminRecord_demoteFromAdmin_deactivatesAdminRecord() {
        User user = user();
        Admin admin = Admin.builder().id("admin-1").userId("u1").status(true).build();
        when(adminRepository.findByUserIdWithUser("u1")).thenReturn(Optional.of(admin));
        when(adminRepository.save(admin)).thenReturn(admin);

        roleAccessSyncService.syncAdminRecord(user, "CLIENT");

        assertThat(admin.isStatus()).isFalse();
        verify(adminRepository).save(admin);
    }

    @Test
    void syncAdminRecord_demoteWithNoExistingAdminRecord_noSave() {
        User user = user();
        when(adminRepository.findByUserIdWithUser("u1")).thenReturn(Optional.empty());

        roleAccessSyncService.syncAdminRecord(user, "CLIENT");

        verify(adminRepository, never()).save(any(Admin.class));
    }
}
