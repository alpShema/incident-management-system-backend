package com.amalitech.hilfe;

import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.AgentGroup;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.services.AgentGroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentGroupServiceTest {

    @Mock AgentGroupRepository agentGroupRepository;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @Mock DepartmentRepository departmentRepository;

    @InjectMocks AgentGroupService agentGroupService;

    @Test
    void removeMember_nonMember_throws404() {
        AgentGroup group = AgentGroup.builder()
                .id("group-1")
                .name("IT Support")
                .status(true)
                .build();

        Agent agent = Agent.builder()
                .id("agent-1")
                .userId("user-1")
                .status(true)
                .build();
        agent.setUser(User.builder().id("user-1").fullName("Agent One").build());

        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(agentRepository.findByIdWithUser("agent-1")).thenReturn(Optional.of(agent));
        when(agentGroupMemberRepository.existsByAgentIdAndAgentGroupId("agent-1", "group-1"))
                .thenReturn(false);

        assertThatThrownBy(() -> agentGroupService.removeMember("group-1", "agent-1"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent is not a member of this agent group")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }
}
