package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AgentGroupMemberResponse;
import com.amalitech.hilfe.dto.AgentGroupRequest;
import com.amalitech.hilfe.dto.AgentGroupResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.AgentGroup;
import com.amalitech.hilfe.models.AgentGroupMember;
import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
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
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final DepartmentRepository departmentRepository;

    public Page<AgentGroupResponse> listAgentGroups(Pageable pageable) {
        return agentGroupRepository.findByStatus(true, pageable)
                .map(this::toResponse);
    }

    public AgentGroupResponse getAgentGroup(String id) {
        return toResponse(findAgentGroup(id));
    }

    @Transactional
    public AgentGroupResponse createAgentGroup(AgentGroupRequest request) {
        if (isBlank(request.name())) {
            throw new ArmsAuthException("Agent group name is required", 400);
        }
        if (isBlank(request.departmentId())) {
            throw new ArmsAuthException("departmentId is required", 400);
        }
        if (agentGroupRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Agent group with this name already exists", 409);
        }

        String groupId = UUID.randomUUID().toString();
        Department department = findActiveDepartment(request.departmentId());

        AgentGroup group = AgentGroup.builder()
                .id(groupId)
                .name(request.name())
                .description(request.description())
                .departmentId(department.getId())
                .status(true)
                .build();
        agentGroupRepository.save(group);

        if (request.agentIds() != null) {
            for (String agentId : request.agentIds()) {
                findAgent(agentId);
                addMembership(agentId, groupId);
            }
        }

        return toResponse(group);
    }

    @Transactional
    public AgentGroupResponse updateAgentGroup(String id, AgentGroupRequest request) {
        AgentGroup group = findAgentGroup(id);
        if (!isBlank(request.name())) {
            if (!group.getName().equalsIgnoreCase(request.name())
                    && agentGroupRepository.existsByNameIgnoreCase(request.name())) {
                throw new ArmsAuthException("Agent group with this name already exists", 409);
            }
            group.setName(request.name());
        }
        if (request.description() != null) {
            group.setDescription(request.description());
        }
        if (!isBlank(request.departmentId())) {
            Department department = findActiveDepartment(request.departmentId());
            group.setDepartmentId(department.getId());
        }
        return toResponse(agentGroupRepository.save(group));
    }

    @Transactional
    public void deleteAgentGroup(String id) {
        AgentGroup group = findAgentGroup(id);
        if (agentGroupMemberRepository.countByAgentGroupId(id) > 0) {
            throw new ArmsAuthException("Agent group has assigned agents", 409);
        }
        group.setStatus(false);
        agentGroupRepository.save(group);
    }

    public List<AgentGroupMemberResponse> listMembers(String agentGroupId) {
        ensureAgentGroupExists(agentGroupId);
        return agentGroupMemberRepository.findAgentsByAgentGroupIdWithUser(agentGroupId).stream()
                .map(AgentGroupMemberResponse::from)
                .toList();
    }

    @Transactional
    public AgentGroupMemberResponse addMember(String agentGroupId, String agentId) {
        ensureAgentGroupExists(agentGroupId);
        Agent agent = findAgentWithUser(agentId);
        addMembership(agentId, agentGroupId);
        return AgentGroupMemberResponse.from(agent);
    }

    @Transactional
    public AgentGroupMemberResponse removeMember(String agentGroupId, String agentId) {
        ensureAgentGroupExists(agentGroupId);
        Agent agent = findAgentWithUser(agentId);
        if (!agentGroupMemberRepository.existsByAgentIdAndAgentGroupId(agentId, agentGroupId)) {
            throw new ArmsAuthException("Agent is not a member of this agent group", 404);
        }
        agentGroupMemberRepository.deleteByAgentIdAndAgentGroupId(agentId, agentGroupId);
        return AgentGroupMemberResponse.from(agent);
    }

    private AgentGroupResponse toResponse(AgentGroup group) {
        LookupResponse department = null;
        if (!isBlank(group.getDepartmentId())) {
            department = departmentRepository.findById(group.getDepartmentId())
                    .map(dept -> LookupResponse.from(dept.getId(), dept.getName()))
                    .orElse(null);
        }
        return AgentGroupResponse.from(
                group,
                department,
                agentGroupMemberRepository.countByAgentGroupId(group.getId()));
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

    private Department findActiveDepartment(String departmentId) {
        return departmentRepository.findById(departmentId)
                .filter(department -> Boolean.TRUE.equals(department.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Department not found", 404));
    }

    private Agent findAgentWithUser(String agentId) {
        return agentRepository.findByIdWithUser(agentId)
                .orElseThrow(() -> new ArmsAuthException("Agent not found", 404));
    }

    private void addMembership(String agentId, String agentGroupId) {
        if (agentGroupMemberRepository.existsByAgentIdAndAgentGroupId(agentId, agentGroupId)) {
            return;
        }
        agentGroupMemberRepository.save(AgentGroupMember.builder()
                .agentId(agentId)
                .agentGroupId(agentGroupId)
                .build());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
