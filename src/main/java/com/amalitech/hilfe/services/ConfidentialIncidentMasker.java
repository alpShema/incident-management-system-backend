package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * HV-1619: masks title/description/creator/assignee/topic on confidential-incident rows for
 * viewers outside the topic's linked owner, in any "browse everything I'm broadly authorized to
 * see" list -- IncidentService's All Incidents / Department Assigned Incidents, and
 * DashboardService's admin/agent-workload incident list. Every other list (my-incidents,
 * assigned-incidents, search) is already scoped to rows the caller unconditionally owns, so
 * they don't need this.
 * <p>
 * Operates on precomputed viewer group IDs (resolved once per page by the caller, not once per
 * row) to avoid an N+1 lookup. For a single incident, see ConfidentialIncidentAccess instead.
 */
@Component
@RequiredArgsConstructor
public class ConfidentialIncidentMasker {

    private final SlaService slaService;
    private final AgentRepository agentRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;

    public Page<IncidentResponse> mask(String userId, List<String> precomputedViewerGroupIds, Page<Incident> incidents) {
        Page<IncidentResponse> responses = slaService.toIncidentResponsePage(incidents);
        List<String> viewerGroupIds = precomputedViewerGroupIds != null
                ? precomputedViewerGroupIds
                : findAgentGroupIds(userId);
        String viewerAgentId = agentRepository.findByUserId(userId).map(Agent::getId).orElse(null);

        List<Incident> incidentList = incidents.getContent();
        List<IncidentResponse> responseList = responses.getContent();
        List<IncidentResponse> masked = new ArrayList<>(responseList.size());
        for (int i = 0; i < responseList.size(); i++) {
            boolean shouldMask = shouldMaskForViewer(userId, viewerGroupIds, viewerAgentId, incidentList.get(i));
            masked.add(shouldMask ? responseList.get(i).masked() : responseList.get(i));
        }
        return new PageImpl<>(masked, responses.getPageable(), responses.getTotalElements());
    }

    private List<String> findAgentGroupIds(String userId) {
        return agentRepository.findByUserId(userId)
                .map(agent -> agentGroupMemberRepository.findAgentGroupIdsByAgentId(agent.getId()))
                .orElse(List.of());
    }

    private boolean shouldMaskForViewer(String userId, List<String> viewerGroupIds, String viewerAgentId, Incident incident) {
        IncidentType topic = incident.getIncidentType();
        if (topic == null || !topic.isConfidential()) {
            return false;
        }
        if (userId.equals(incident.getUserId())) {
            return false;
        }
        if (viewerAgentId != null && viewerAgentId.equals(incident.getAssignedToId())) {
            return false;
        }
        return !isMemberOfTopicOwner(topic, viewerGroupIds, viewerAgentId);
    }

    private boolean isMemberOfTopicOwner(IncidentType topic, List<String> viewerGroupIds, String viewerAgentId) {
        if (StringUtils.hasText(topic.getAgentGroupId())) {
            return viewerGroupIds.contains(topic.getAgentGroupId());
        }
        if (StringUtils.hasText(topic.getAgentId())) {
            return topic.getAgentId().equals(viewerAgentId);
        }
        return false;
    }
}
