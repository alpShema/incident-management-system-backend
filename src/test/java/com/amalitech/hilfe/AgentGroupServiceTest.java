package com.amalitech.hilfe;

import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.AgentGroup;
import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.AgentGroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentGroupServiceTest {

    @Mock AgentGroupRepository agentGroupRepository;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ActivityLogService activityLogService;

    @InjectMocks AgentGroupService agentGroupService;

    private AgentGroup group(boolean status) {
        return AgentGroup.builder()
                .id("group-1")
                .name("IT Support")
                .departmentId("dept-1")
                .status(status)
                .build();
    }

    private Agent memberAgent() {
        Agent agent = Agent.builder()
                .id("agent-1")
                .userId("user-1")
                .status(true)
                .build();
        agent.setUser(User.builder().id("user-1").fullName("Agent One").build());
        return agent;
    }

    @Test
    void createAgentGroup_withDeactivatedAgent_throws400() {
        Department dept = Department.builder().id("dept-1").name("Facilities").status(true).build();
        Agent activeAgent = Agent.builder().id("agent-active").userId("user-active").status(true).build();
        Agent inactiveAgent = Agent.builder().id("agent-inactive").userId("user-inactive").status(false).build();

        when(agentGroupRepository.existsByNameIgnoreCase("Test Group")).thenReturn(false);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(agentRepository.findById("agent-active")).thenReturn(Optional.of(activeAgent));
        when(agentRepository.findById("agent-inactive")).thenReturn(Optional.of(inactiveAgent));

        var request = new com.amalitech.hilfe.dto.AgentGroupRequest("Test Group", null, "dept-1", List.of("agent-active", "agent-inactive"));

        assertThatThrownBy(() -> agentGroupService.createAgentGroup(request))
                .isInstanceOf(com.amalitech.hilfe.exceptions.ArmsAuthException.class)
                .hasMessage("Agent not found or inactive")
                .extracting(e -> ((com.amalitech.hilfe.exceptions.ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);

        verify(agentGroupRepository, never()).save(any(AgentGroup.class));
    }

    @Test
    void createAgentGroup_withOnlyDeactivatedAgent_throws400() {
        Department dept = Department.builder().id("dept-1").name("Facilities").status(true).build();
        Agent inactiveAgent = Agent.builder().id("agent-inactive").userId("user-inactive").status(false).build();

        when(agentGroupRepository.existsByNameIgnoreCase("Test Group")).thenReturn(false);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(agentRepository.findById("agent-inactive")).thenReturn(Optional.of(inactiveAgent));

        var request = new com.amalitech.hilfe.dto.AgentGroupRequest("Test Group", null, "dept-1", List.of("agent-inactive"));

        assertThatThrownBy(() -> agentGroupService.createAgentGroup(request))
                .isInstanceOf(com.amalitech.hilfe.exceptions.ArmsAuthException.class)
                .hasMessage("Agent not found or inactive")
                .extracting(e -> ((com.amalitech.hilfe.exceptions.ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);

        verify(agentGroupRepository, never()).save(any(AgentGroup.class));
    }

    @Test
    void createAgentGroup_trimsNameAndDescription() {
        Department dept = Department.builder().id("dept-1").name("Facilities").status(true).build();

        when(agentGroupRepository.existsByNameIgnoreCase("Support Team")).thenReturn(false);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept));
        when(agentGroupRepository.save(any(AgentGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new com.amalitech.hilfe.dto.AgentGroupRequest(" Support Team ", " Handles tickets ", "dept-1", null);
        agentGroupService.createAgentGroup(request);

        var captor = ArgumentCaptor.forClass(AgentGroup.class);
        verify(agentGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Support Team");
        assertThat(captor.getValue().getDescription()).isEqualTo("Handles tickets");
    }

    @Test
    void updateAgentGroup_trimsNameAndDescription() {
        AgentGroup group = group(true);  // name = "IT Support"
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(agentGroupRepository.save(group)).thenReturn(group);

        // name " New Name " differs from existing "IT Support" — dedup check will fire
        when(agentGroupRepository.existsByNameIgnoreCase("New Name")).thenReturn(false);

        var request = new com.amalitech.hilfe.dto.AgentGroupRequest(" New Name ", " Updated desc ", null, null);
        agentGroupService.updateAgentGroup("group-1", request);

        assertThat(group.getName()).isEqualTo("New Name");
        assertThat(group.getDescription()).isEqualTo("Updated desc");
    }

    @Test
    void updateAgentGroup_syncsMembership() {
        AgentGroup group = group(true);
        Agent agentB = Agent.builder().id("agent-B").userId("user-B").status(true).build();

        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(agentGroupRepository.save(group)).thenReturn(group);
        when(agentRepository.findById("agent-B")).thenReturn(Optional.of(agentB));

        var request = new com.amalitech.hilfe.dto.AgentGroupRequest(null, null, null, List.of("agent-B"));
        agentGroupService.updateAgentGroup("group-1", request);

        verify(agentGroupMemberRepository).deleteByAgentGroupId("group-1");
        verify(agentGroupMemberRepository).existsByAgentIdAndAgentGroupId("agent-B", "group-1");
    }

    @Test
    void updateAgentGroup_nullAgentIds_doesNotTouchMembership() {
        AgentGroup group = group(true);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(agentGroupRepository.save(group)).thenReturn(group);

        var request = new com.amalitech.hilfe.dto.AgentGroupRequest(" New Name ", null, null, null);
        when(agentGroupRepository.existsByNameIgnoreCase("New Name")).thenReturn(false);
        agentGroupService.updateAgentGroup("group-1", request);

        verify(agentGroupMemberRepository, never()).deleteByAgentGroupId(any());
    }

    @Test
    void updateAgentGroup_withDeactivatedAgentInList_throws400() {
        AgentGroup group = group(true);
        Agent inactiveAgent = Agent.builder().id("agent-inactive").userId("user-inactive").status(false).build();

        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(agentGroupRepository.save(group)).thenReturn(group);
        when(agentRepository.findById("agent-inactive")).thenReturn(Optional.of(inactiveAgent));

        var request = new com.amalitech.hilfe.dto.AgentGroupRequest(null, null, null, List.of("agent-inactive"));

        assertThatThrownBy(() -> agentGroupService.updateAgentGroup("group-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent not found or inactive")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);

        verify(agentGroupMemberRepository, never()).deleteByAgentGroupId(any());
    }

    @Test
    void updateAgentGroupStatus_deactivate_succeedsAndPreservesMembers() {
        AgentGroup group = group(true);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(agentGroupRepository.save(group)).thenReturn(group);

        agentGroupService.updateAgentGroupStatus("admin-1", "group-1", false);

        assertThat(group.getStatus()).isFalse();
        verify(agentGroupRepository).save(group);
        verify(activityLogService).logAgentGroupStatusChange("admin-1", "group-1", true, false);
    }

    @Test
    void updateAgentGroupStatus_activate_succeeds() {
        AgentGroup group = group(false);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(agentGroupRepository.save(group)).thenReturn(group);

        agentGroupService.updateAgentGroupStatus("admin-1", "group-1", true);

        assertThat(group.getStatus()).isTrue();
        verify(agentGroupRepository).save(group);
        verify(activityLogService).logAgentGroupStatusChange("admin-1", "group-1", false, true);
    }

    @Test
    void updateAgentGroupStatus_alreadyInactive_throws409() {
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group(false)));

        assertThatThrownBy(() -> agentGroupService.updateAgentGroupStatus("admin-1", "group-1", false))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent group is already inactive")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void updateAgentGroupStatus_alreadyActive_throws409() {
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group(true)));

        assertThatThrownBy(() -> agentGroupService.updateAgentGroupStatus("admin-1", "group-1", true))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent group is already active")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void updateAgentGroupStatus_notFound_throws404() {
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentGroupService.updateAgentGroupStatus("admin-1", "group-1", true))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent group not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void getAgentGroup_inactive_returnsResponse() {
        AgentGroup group = group(false);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(agentGroupMemberRepository.countByAgentGroupId("group-1")).thenReturn(1L);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(
                Department.builder().id("dept-1").name("Facilities").status(false).build()));

        var response = agentGroupService.getAgentGroup("group-1");

        assertThat(response.id()).isEqualTo("group-1");
        assertThat(response.status()).isFalse();
        assertThat(response.memberCount()).isEqualTo(1L);
    }

    @Test
    void listMembers_inactiveGroup_returnsMembers() {
        AgentGroup group = group(false);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(agentGroupMemberRepository.findAgentsByAgentGroupIdWithUser("group-1"))
                .thenReturn(List.of(memberAgent()));

        var members = agentGroupService.listMembers("group-1");

        assertThat(members).hasSize(1);
        assertThat(members.getFirst().agentId()).isEqualTo("agent-1");
    }

    @Test
    void addMember_inactiveGroup_throws404() {
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group(false)));

        assertThatThrownBy(() -> agentGroupService.addMember("group-1", "agent-2"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent group not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

}
