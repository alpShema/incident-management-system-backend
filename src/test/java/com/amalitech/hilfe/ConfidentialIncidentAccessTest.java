package com.amalitech.hilfe;

import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.services.ConfidentialIncidentAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfidentialIncidentAccessTest {

    @Mock AgentRepository agentRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @InjectMocks ConfidentialIncidentAccess confidentialIncidentAccess;

    private Incident incidentWithTopic(IncidentType topic, String userId, String assignedToId) {
        Incident incident = Incident.builder().id("inc-1").userId(userId).assignedToId(assignedToId).build();
        incident.setIncidentType(topic);
        return incident;
    }

    @Test
    void canAccess_nonConfidentialTopic_alwaysTrue() {
        IncidentType topic = IncidentType.builder().id("type-1").agentGroupId("group-1").confidential(false).build();
        Incident incident = incidentWithTopic(topic, "creator", null);

        assertThat(confidentialIncidentAccess.canAccess("anyone", incident)).isTrue();
    }

    @Test
    void canAccess_nullTopic_alwaysTrue() {
        Incident incident = Incident.builder().id("inc-1").userId("creator").build();

        assertThat(confidentialIncidentAccess.canAccess("anyone", incident)).isTrue();
    }

    @Test
    void canAccess_creator_true() {
        IncidentType topic = IncidentType.builder().id("type-1").agentGroupId("group-1").confidential(true).build();
        Incident incident = incidentWithTopic(topic, "creator-1", null);

        assertThat(confidentialIncidentAccess.canAccess("creator-1", incident)).isTrue();
    }

    @Test
    void canAccess_assignee_true() {
        IncidentType topic = IncidentType.builder().id("type-1").agentGroupId("group-1").confidential(true).build();
        Incident incident = incidentWithTopic(topic, "creator-1", "agent-1");
        when(agentRepository.findByUserId("agent-user-1")).thenReturn(Optional.of(
                Agent.builder().id("agent-1").userId("agent-user-1").build()));

        assertThat(confidentialIncidentAccess.canAccess("agent-user-1", incident)).isTrue();
    }

    @Test
    void canAccess_groupMember_true() {
        IncidentType topic = IncidentType.builder().id("type-1").agentGroupId("group-1").confidential(true).build();
        Incident incident = incidentWithTopic(topic, "creator-1", "some-other-agent");
        when(agentRepository.findByUserId("member-user")).thenReturn(Optional.of(
                Agent.builder().id("member-agent").userId("member-user").build()));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("member-agent")).thenReturn(List.of("group-1"));

        assertThat(confidentialIncidentAccess.canAccess("member-user", incident)).isTrue();
    }

    @Test
    void canAccess_agentInDifferentGroup_false() {
        IncidentType topic = IncidentType.builder().id("type-1").agentGroupId("group-1").confidential(true).build();
        Incident incident = incidentWithTopic(topic, "creator-1", "some-other-agent");
        when(agentRepository.findByUserId("outsider-user")).thenReturn(Optional.of(
                Agent.builder().id("outsider-agent").userId("outsider-user").build()));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("outsider-agent")).thenReturn(List.of("group-2"));

        assertThat(confidentialIncidentAccess.canAccess("outsider-user", incident)).isFalse();
    }

    @Test
    void canAccess_noAgentRecord_false() {
        IncidentType topic = IncidentType.builder().id("type-1").agentGroupId("group-1").confidential(true).build();
        Incident incident = incidentWithTopic(topic, "creator-1", "some-other-agent");
        when(agentRepository.findByUserId("client-user")).thenReturn(Optional.empty());

        assertThat(confidentialIncidentAccess.canAccess("client-user", incident)).isFalse();
    }

    @Test
    void canAccess_nullUserId_false() {
        IncidentType topic = IncidentType.builder().id("type-1").agentGroupId("group-1").confidential(true).build();
        Incident incident = incidentWithTopic(topic, "creator-1", null);

        assertThat(confidentialIncidentAccess.canAccess(null, incident)).isFalse();
    }

    @Test
    void canAccess_legacySingleAgentTopic_ownerTrue_othersFalse() {
        IncidentType topic = IncidentType.builder().id("type-1").agentId("solo-agent").confidential(true).build();
        Incident incident = incidentWithTopic(topic, "creator-1", null);
        when(agentRepository.findByUserId("solo-agent-user")).thenReturn(Optional.of(
                Agent.builder().id("solo-agent").userId("solo-agent-user").build()));
        when(agentRepository.findByUserId("someone-else")).thenReturn(Optional.of(
                Agent.builder().id("other-agent").userId("someone-else").build()));

        assertThat(confidentialIncidentAccess.canAccess("solo-agent-user", incident)).isTrue();
        assertThat(confidentialIncidentAccess.canAccess("someone-else", incident)).isFalse();
    }

    @Test
    void canAccess_topicWithNoOwnerAtAll_false() {
        IncidentType topic = IncidentType.builder().id("type-1").confidential(true).build();
        Incident incident = incidentWithTopic(topic, "creator-1", null);
        when(agentRepository.findByUserId("anyone")).thenReturn(Optional.empty());

        assertThat(confidentialIncidentAccess.canAccess("anyone", incident)).isFalse();
    }
}
