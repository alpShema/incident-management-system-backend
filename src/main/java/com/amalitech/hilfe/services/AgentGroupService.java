package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AgentGroupMemberResponse;
import com.amalitech.hilfe.dto.AgentGroupRequest;
import com.amalitech.hilfe.dto.AgentGroupResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.AgentGroup;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentGroupService {
    private final AgentGroupRepository agentGroupRepository;
    private final AgentRepository agentRepository;

    public List<AgentGroupResponse> listDepartments() {
        return agentGroupRepository.findByStatus(true).stream()
                .map(this::toResponse)
                .toList();
    }

    public AgentGroupResponse getDepartment(String id) {
        return toResponse(findDepartment(id));
    }

    @Transactional
    public AgentGroupResponse createDepartment(AgentGroupRequest request) {
        if (agentGroupRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Department with this name already exists", 409);
        }

        AgentGroup group = AgentGroup.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .status(true)
                .build();
        return toResponse(agentGroupRepository.save(group));
    }

    @Transactional
    public AgentGroupResponse updateDepartment(String id, AgentGroupRequest request) {
        AgentGroup group = findDepartment(id);
        if (!group.getName().equalsIgnoreCase(request.name())
                && agentGroupRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Department with this name already exists", 409);
        }

        group.setName(request.name());
        group.setDescription(request.description());
        return toResponse(agentGroupRepository.save(group));
    }

    @Transactional
    public void deleteDepartment(String id) {
        AgentGroup group = findDepartment(id);
        if (agentRepository.countByAgentGroupId(id) > 0) {
            throw new ArmsAuthException("Department has assigned agents", 409);
        }
        group.setStatus(false);
        agentGroupRepository.save(group);
    }

    public List<AgentGroupMemberResponse> listMembers(String departmentId) {
        ensureDepartmentExists(departmentId);
        return agentRepository.findByAgentGroupIdWithUser(departmentId).stream()
                .map(AgentGroupMemberResponse::from)
                .toList();
    }

    @Transactional
    public AgentGroupMemberResponse addMember(String departmentId, String agentId) {
        ensureDepartmentExists(departmentId);
        Agent agent = findAgent(agentId);
        agent.setAgentGroupId(departmentId);
        return AgentGroupMemberResponse.from(agentRepository.save(agent));
    }

    @Transactional
    public AgentGroupMemberResponse removeMember(String departmentId, String agentId) {
        ensureDepartmentExists(departmentId);
        Agent agent = findAgent(agentId);
        if (!departmentId.equals(agent.getAgentGroupId())) {
            throw new ArmsAuthException("Agent is not a member of this department", 400);
        }
        agent.setAgentGroupId(null);
        return AgentGroupMemberResponse.from(agentRepository.save(agent));
    }

    private AgentGroupResponse toResponse(AgentGroup group) {
        return AgentGroupResponse.from(group, agentRepository.countByAgentGroupId(group.getId()));
    }

    private AgentGroup findDepartment(String id) {
        return agentGroupRepository.findById(id)
                .filter(group -> Boolean.TRUE.equals(group.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Department not found", 404));
    }

    private void ensureDepartmentExists(String id) {
        findDepartment(id);
    }

    private Agent findAgent(String agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new ArmsAuthException("Agent not found", 404));
    }
}
