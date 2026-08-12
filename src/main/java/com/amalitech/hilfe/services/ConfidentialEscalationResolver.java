package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * HV-1619: confidential incidents never escalate to admins or department heads -- escalation
 * stays within the topic's linked agent group (or single agent, for legacy topics). Shared by
 * IncidentService (initial auto-assignment escalation) and SlaService (SLA-breach escalation)
 * so the two don't drift out of sync with each other.
 */
@Component
@RequiredArgsConstructor
public class ConfidentialEscalationResolver {

    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final AgentRepository agentRepository;

    public List<String> resolveRecipientUserIds(IncidentType incidentType) {
        if (StringUtils.hasText(incidentType.getAgentGroupId())) {
            return agentGroupMemberRepository.findAgentsByAgentGroupIdWithUser(incidentType.getAgentGroupId())
                    .stream()
                    .filter(a -> a.getUser() != null && Boolean.TRUE.equals(a.getUser().getStatus()))
                    .map(a -> a.getUser().getId())
                    .distinct()
                    .toList();
        }
        if (StringUtils.hasText(incidentType.getAgentId())) {
            return agentRepository.findByIdWithUser(incidentType.getAgentId())
                    .filter(a -> a.getUser() != null && Boolean.TRUE.equals(a.getUser().getStatus()))
                    .map(a -> List.of(a.getUser().getId()))
                    .orElse(List.of());
        }
        return List.of();
    }
}
