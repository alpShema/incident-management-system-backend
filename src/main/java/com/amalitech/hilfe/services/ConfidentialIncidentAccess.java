package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * HV-1619: the single predicate for "may this user see/act on this incident's confidential
 * detail" -- the creator, the assignee, or a member of the topic's linked agent group (or
 * single agent, for legacy topics). Always true for non-confidential incidents.
 * <p>
 * Shared by every surface that touches a confidential incident (detail view, activity log, and
 * the assign/severity/status mutations) so they can't drift out of sync with each other. List
 * views (All Incidents / Department Assigned Incidents) don't use this directly -- they resolve
 * the viewer's group IDs once per page instead of once per row; see
 * IncidentService#shouldMaskForViewer.
 */
@Component
@RequiredArgsConstructor
public class ConfidentialIncidentAccess {

    private final AgentRepository agentRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;

    public boolean canAccess(String userId, Incident incident) {
        IncidentType topic = incident.getIncidentType();
        if (topic == null || !topic.isConfidential()) {
            return true;
        }
        if (userId != null && userId.equals(incident.getUserId())) {
            return true;
        }
        Optional<Agent> agent = userId == null ? Optional.empty() : agentRepository.findByUserId(userId);
        if (agent.map(a -> a.getId().equals(incident.getAssignedToId())).orElse(false)) {
            return true;
        }
        return isMemberOfTopicOwner(agent, topic);
    }

    private boolean isMemberOfTopicOwner(Optional<Agent> agent, IncidentType topic) {
        if (StringUtils.hasText(topic.getAgentGroupId())) {
            return agent.map(a -> agentGroupMemberRepository.findAgentGroupIdsByAgentId(a.getId()))
                    .map(groupIds -> groupIds.contains(topic.getAgentGroupId()))
                    .orElse(false);
        }
        if (StringUtils.hasText(topic.getAgentId())) {
            return agent.map(a -> a.getId().equals(topic.getAgentId())).orElse(false);
        }
        return false;
    }
}
