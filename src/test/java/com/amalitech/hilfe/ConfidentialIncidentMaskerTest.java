package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.services.ConfidentialIncidentMasker;
import com.amalitech.hilfe.services.SlaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfidentialIncidentMaskerTest {

    @Mock SlaService slaService;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @InjectMocks ConfidentialIncidentMasker masker;

    private Incident confidentialIncident(String userId, String assignedToId, String agentGroupId) {
        IncidentType topic = IncidentType.builder()
                .id("type-1").agentGroupId(agentGroupId).confidential(true).build();
        Incident incident = Incident.builder().id("inc-1").userId(userId).assignedToId(assignedToId).build();
        incident.setIncidentNo(1);
        incident.setIncidentType(topic);
        return incident;
    }

    private void stubConversion(Page<Incident> page) {
        when(slaService.toIncidentResponsePage(page)).thenAnswer(inv ->
                new PageImpl<>(page.getContent().stream().map(i -> IncidentResponse.from(i, null, null)).toList(),
                        page.getPageable(), page.getTotalElements()));
    }

    @Test
    void mask_nonConfidentialIncident_isNeverMasked() {
        Incident incident = Incident.builder().id("inc-1").userId("someone-else").title("Visible title").build();
        incident.setIncidentNo(1);
        Page<Incident> page = new PageImpl<>(List.of(incident));
        stubConversion(page);

        Page<IncidentResponse> result = masker.mask("viewer", List.of(), page);

        assertThat(result.getContent().get(0).title()).isEqualTo("Visible title");
    }

    @Test
    void mask_creator_isNeverMasked() {
        Incident incident = confidentialIncident("creator-1", null, "group-1");
        incident.setTitle("Secret title");
        Page<Incident> page = new PageImpl<>(List.of(incident));
        stubConversion(page);

        Page<IncidentResponse> result = masker.mask("creator-1", List.of(), page);

        assertThat(result.getContent().get(0).title()).isEqualTo("Secret title");
    }

    @Test
    void mask_assignee_isNeverMasked() {
        Incident incident = confidentialIncident("creator-1", "agent-1", "group-1");
        incident.setTitle("Secret title");
        Page<Incident> page = new PageImpl<>(List.of(incident));
        stubConversion(page);
        when(agentRepository.findByUserId("agent-user")).thenReturn(Optional.of(
                Agent.builder().id("agent-1").userId("agent-user").build()));

        Page<IncidentResponse> result = masker.mask("agent-user", List.of(), page);

        assertThat(result.getContent().get(0).title()).isEqualTo("Secret title");
    }

    @Test
    void mask_viewerInPrecomputedGroup_isNeverMasked() {
        Incident incident = confidentialIncident("creator-1", null, "group-1");
        incident.setTitle("Secret title");
        Page<Incident> page = new PageImpl<>(List.of(incident));
        stubConversion(page);

        // No agentRepository stub needed -- group membership alone is enough to pass, and
        // the precomputed group list means no per-row lookup happens either.
        Page<IncidentResponse> result = masker.mask("member-user", List.of("group-1"), page);

        assertThat(result.getContent().get(0).title()).isEqualTo("Secret title");
    }

    @Test
    void mask_outsider_masksTitleCreatorAssigneeAndTopic() {
        Incident incident = confidentialIncident("creator-1", null, "group-1");
        incident.setTitle("Secret title");
        incident.setDescription("Secret description");
        Page<Incident> page = new PageImpl<>(List.of(incident));
        stubConversion(page);

        Page<IncidentResponse> result = masker.mask("outsider", List.of("group-2"), page);

        IncidentResponse row = result.getContent().get(0);
        assertThat(row.confidential()).isTrue();
        assertThat(row.title()).isNull();
        assertThat(row.description()).isNull();
        assertThat(row.createdBy()).isNull();
        assertThat(row.assignedTo()).isNull();
        assertThat(row.incidentTopic()).isNull();
        assertThat(row.id()).isEqualTo("inc-1");
    }

    @Test
    void mask_nullPrecomputedGroups_resolvesFromRepositories() {
        Incident incident = confidentialIncident("creator-1", null, "group-1");
        incident.setTitle("Secret title");
        Page<Incident> page = new PageImpl<>(List.of(incident));
        stubConversion(page);
        when(agentRepository.findByUserId("member-user")).thenReturn(Optional.of(
                Agent.builder().id("member-agent").userId("member-user").build()));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("member-agent")).thenReturn(List.of("group-1"));

        Page<IncidentResponse> result = masker.mask("member-user", null, page);

        assertThat(result.getContent().get(0).title()).isEqualTo("Secret title");
    }
}
