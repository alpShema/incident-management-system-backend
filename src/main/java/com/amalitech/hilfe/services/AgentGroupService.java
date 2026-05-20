package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AgentGroupMemberResponse;
import com.amalitech.hilfe.dto.AgentGroupRequest;
import com.amalitech.hilfe.dto.AgentGroupResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.AgentGroup;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentGroupService {
    private final AgentGroupRepository agentGroupRepository;
    private final AgentRepository agentRepository;

    public Page<AgentGroupResponse> listAgentGroups(Pageable pageable) {
        return agentGroupRepository.findByStatus(true, pageable)
                .map(this::toResponse);
    }

    public AgentGroupResponse getAgentGroup(String id) {
        return toResponse(findAgentGroup(id));
    }

    @Transactional
    public AgentGroupResponse createAgentGroup(AgentGroupRequest request) {
        if (agentGroupRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Agent group with this name already exists", 409);
        }

        String groupId = UUID.randomUUID().toString();
        Agent primaryAgent = null;
        if (request.primaryAgentId() != null && !request.primaryAgentId().isBlank()) {
            primaryAgent = findAgent(request.primaryAgentId());
        }

        AgentGroup group = AgentGroup.builder()
                .id(groupId)
                .name(request.name())
                .description(request.description())
                .primaryAgentId(primaryAgent != null ? primaryAgent.getId() : null)
                .status(true)
                .build();
        AgentGroup saved = agentGroupRepository.save(group);
        if (primaryAgent != null) {
            primaryAgent.setAgentGroupId(saved.getId());
            agentRepository.save(primaryAgent);
        }
        return toResponse(saved);
    }

    @Transactional
    public AgentGroupResponse updateAgentGroup(String id, AgentGroupRequest request) {
        AgentGroup group = findAgentGroup(id);
        if (!group.getName().equalsIgnoreCase(request.name())
                && agentGroupRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Agent group with this name already exists", 409);
        }

        group.setName(request.name());
        group.setDescription(request.description());
        if (request.primaryAgentId() != null && !request.primaryAgentId().isBlank()) {
            Agent primaryAgent = findAgent(request.primaryAgentId());
            if (!id.equals(primaryAgent.getAgentGroupId())) {
                throw new ArmsAuthException("Primary agent must be a member of this agent group", 400);
            }
            group.setPrimaryAgentId(primaryAgent.getId());
        } else {
            group.setPrimaryAgentId(null);
        }
        return toResponse(agentGroupRepository.save(group));
    }

    @Transactional
    public void deleteAgentGroup(String id) {
        AgentGroup group = findAgentGroup(id);
        if (agentRepository.countByAgentGroupId(id) > 0) {
            throw new ArmsAuthException("Agent group has assigned agents", 409);
        }
        group.setStatus(false);
        agentGroupRepository.save(group);
    }

    public List<AgentGroupMemberResponse> listMembers(String agentGroupId) {
        ensureAgentGroupExists(agentGroupId);
        return agentRepository.findByAgentGroupIdWithUser(agentGroupId).stream()
                .map(AgentGroupMemberResponse::from)
                .toList();
    }

    @Transactional
    public AgentGroupMemberResponse addMember(String agentGroupId, String agentId) {
        ensureAgentGroupExists(agentGroupId);
        Agent agent = findAgent(agentId);
        agent.setAgentGroupId(agentGroupId);
        return AgentGroupMemberResponse.from(agentRepository.save(agent));
    }

    @Transactional
    public AgentGroupMemberResponse removeMember(String agentGroupId, String agentId) {
        AgentGroup group = findAgentGroup(agentGroupId);
        Agent agent = findAgent(agentId);
        if (!agentGroupId.equals(agent.getAgentGroupId())) {
            throw new ArmsAuthException("Agent is not a member of this agent group", 400);
        }
        if (agentId.equals(group.getPrimaryAgentId())) {
            group.setPrimaryAgentId(null);
            agentGroupRepository.save(group);
        }
        agent.setAgentGroupId(null);
        return AgentGroupMemberResponse.from(agentRepository.save(agent));
    }

    private AgentGroupResponse toResponse(AgentGroup group) {
        LookupResponse primaryAgent = null;
        if (group.getPrimaryAgentId() != null && !group.getPrimaryAgentId().isBlank()) {
            primaryAgent = agentRepository.findByIdWithUser(group.getPrimaryAgentId())
                    .map(agent -> LookupResponse.from(
                            agent.getId(),
                            agent.getUser() != null ? agent.getUser().getFullName() : agent.getUserId()))
                    .orElse(null);
        }
        return AgentGroupResponse.from(group, primaryAgent, agentRepository.countByAgentGroupId(group.getId()));
    }

    private AgentGroup findAgentGroup(String id) {
        return agentGroupRepository.findById(id)
                .filter(group -> Boolean.TRUE.equals(group.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Agent group not found", 404));
    }

    private void ensureAgentGroupExists(String id) {
        findAgentGroup(id);
    }

    private Agent findAgent(String agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new ArmsAuthException("Agent not found", 404));
    }
}
