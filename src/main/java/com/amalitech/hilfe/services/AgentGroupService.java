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
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
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
    private final IncidentTypeRepository incidentTypeRepository;
    private final ActivityLogService activityLogService;

    private static final String TOPIC_NOT_FOUND_PREFIX = "Topic not found: ";

    public Page<AgentGroupResponse> listAgentGroups(String query, String departmentId, Pageable pageable) {
        String queryPattern = (query == null || query.isBlank()) ? null : "%" + query.toLowerCase() + "%";
        return agentGroupRepository.searchAgentGroups(queryPattern, departmentId, pageable)
                .map(this::toResponse);
    }

    public Page<AgentGroupResponse> listAllAgentGroups(String status, String query, String departmentId, Pageable pageable) {
        Boolean statusFilter = (status == null || status.isBlank() || status.equalsIgnoreCase("all"))
                ? null
                : resolveStatusFilter(status);
        String queryPattern = (query == null || query.isBlank()) ? null : "%" + query.toLowerCase() + "%";
        return agentGroupRepository.listAllAgentGroups(statusFilter, queryPattern, departmentId, pageable)
                .map(this::toResponse);
    }

    private boolean resolveStatusFilter(String status) {
        if (status.equalsIgnoreCase("active")) return true;
        if (status.equalsIgnoreCase("deactivated")) return false;
        throw new ArmsAuthException("Invalid status filter. Accepted values: active, deactivated, all", 400);
    }

    public AgentGroupResponse getAgentGroup(String id) {
        return toResponse(findAgentGroupByIdOrThrow(id));
    }

    @Transactional
    public AgentGroupResponse createAgentGroup(AgentGroupRequest request) {
        String name = trimOrNull(request.name());
        String description = trimOrNull(request.description());

        if (isBlank(name)) {
            throw new ArmsAuthException("Agent group name is required", 400);
        }
        if (isBlank(request.departmentId())) {
            throw new ArmsAuthException("departmentId is required", 400);
        }
        if (agentGroupRepository.existsByNameIgnoreCase(name)) {
            throw new ArmsAuthException("Agent group with this name already exists", 409);
        }
        if (request.agentIds() == null || request.agentIds().isEmpty()) {
            throw new ArmsAuthException("At least one agent is required", 400);
        }

        String groupId = UUID.randomUUID().toString();
        Department department = findActiveDepartment(request.departmentId());

        for (String agentId : request.agentIds()) {
            findActiveAgent(agentId);
        }

        AgentGroup group = AgentGroup.builder()
                .id(groupId)
                .name(name)
                .description(description)
                .departmentId(department.getId())
                .status(true)
                .build();
        if (request.topicIds() != null) {
            validateTopicsForDepartment(request.topicIds(), department.getId());
        }

        agentGroupRepository.save(group);

        for (String agentId : request.agentIds()) {
            addMembership(agentId, groupId);
        }

        if (request.topicIds() != null) {
            for (String topicId : request.topicIds()) {
                IncidentType topic = incidentTypeRepository.findById(topicId)
                        .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND_PREFIX + topicId, 404));
                topic.setAgentGroupId(groupId);
                incidentTypeRepository.save(topic);
            }
            incidentTypeRepository.flush();
        }

        return toResponse(group);
    }

    @Transactional
    public AgentGroupResponse updateAgentGroup(String id, AgentGroupRequest request) {
        String name = trimOrNull(request.name());
        String description = trimOrNull(request.description());

        AgentGroup group = findActiveAgentGroupOrThrow(id);
        if (!isBlank(name)) {
            if (!group.getName().equalsIgnoreCase(name)
                    && agentGroupRepository.existsByNameIgnoreCase(name)) {
                throw new ArmsAuthException("Agent group with this name already exists", 409);
            }
            group.setName(name);
        }
        if (description != null) {
            group.setDescription(description);
        }
        if (!isBlank(request.departmentId())) {
            Department department = findActiveDepartment(request.departmentId());
            group.setDepartmentId(department.getId());
        }
        agentGroupRepository.save(group);

        if (request.agentIds() != null) {
            for (String agentId : request.agentIds()) {
                findActiveAgent(agentId);
            }
            agentGroupMemberRepository.deleteByAgentGroupId(id);
            for (String agentId : request.agentIds()) {
                addMembership(agentId, id);
            }
        }

        if (request.topicIds() != null) {
            validateTopicsForDepartment(request.topicIds(), group.getDepartmentId());
            incidentTypeRepository.clearAgentGroupId(id);
            for (String topicId : request.topicIds()) {
                IncidentType topic = incidentTypeRepository.findById(topicId)
                        .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND_PREFIX + topicId, 404));
                topic.setAgentGroupId(id);
                incidentTypeRepository.save(topic);
            }
            incidentTypeRepository.flush();
        }

        return toResponse(group);
    }

    @Transactional
    public AgentGroupResponse updateAgentGroupStatus(String actorUserId, String id, Boolean status) {
        AgentGroup group = findAgentGroupByIdOrThrow(id);

        if (status.equals(group.getStatus())) {
            throw new ArmsAuthException(
                    Boolean.TRUE.equals(status)
                            ? "Agent group is already active"
                            : "Agent group is already inactive",
                    409);
        }

        Boolean previousStatus = group.getStatus();
        group.setStatus(status);
        AgentGroup saved = agentGroupRepository.save(group);
        activityLogService.logAgentGroupStatusChange(actorUserId, id, previousStatus, status);
        return toResponse(saved);
    }

    public List<AgentGroupMemberResponse> listMembers(String agentGroupId) {
        ensureAgentGroupExists(agentGroupId);
        return agentGroupMemberRepository.findAgentsByAgentGroupIdWithUser(agentGroupId).stream()
                .map(AgentGroupMemberResponse::from)
                .toList();
    }

    @Transactional
    public AgentGroupMemberResponse addMember(String agentGroupId, String agentId) {
        ensureActiveAgentGroupExists(agentGroupId);
        Agent agent = findAgentWithUser(agentId);
        addMembership(agentId, agentGroupId);
        return AgentGroupMemberResponse.from(agent);
    }

    private AgentGroupResponse toResponse(AgentGroup group) {
        LookupResponse department = null;
        if (!isBlank(group.getDepartmentId())) {
            department = departmentRepository.findById(group.getDepartmentId())
                    .map(dept -> LookupResponse.from(dept.getId(), dept.getName()))
                    .orElse(null);
        }
        List<LookupResponse> topics = incidentTypeRepository.findByAgentGroupId(group.getId()).stream()
                .map(t -> LookupResponse.from(t.getId(), t.getName()))
                .toList();
        return AgentGroupResponse.from(
                group,
                department,
                agentGroupMemberRepository.countByAgentGroupId(group.getId()),
                topics);
    }

    private void validateTopicsForDepartment(List<String> topicIds, String departmentId) {
        for (String topicId : topicIds) {
            IncidentType topic = incidentTypeRepository.findById(topicId)
                    .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND_PREFIX + topicId, 404));
            String topicDeptId = topic.getCategory() != null ? topic.getCategory().getDepartmentId() : null;
            if (topicDeptId == null || !topicDeptId.equals(departmentId)) {
                throw new ArmsAuthException("Topic '" + topic.getName() + "' does not belong to the selected department", 400);
            }
        }
    }

    private AgentGroup findAgentGroupByIdOrThrow(String id) {
        return agentGroupRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Agent group not found", 404));
    }

    private AgentGroup findActiveAgentGroupOrThrow(String id) {
        return agentGroupRepository.findById(id)
                .filter(group -> Boolean.TRUE.equals(group.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Agent group not found", 404));
    }

    private void ensureAgentGroupExists(String id) {
        findAgentGroupByIdOrThrow(id);
    }

    private void ensureActiveAgentGroupExists(String id) {
        findActiveAgentGroupOrThrow(id);
    }

    private Agent findActiveAgent(String agentId) {
        Agent agent = agentRepository.findByIdWithUser(agentId)
                .orElseThrow(() -> new ArmsAuthException("Agent not found or inactive", 400));
        if (agent.getUser() == null || !Boolean.TRUE.equals(agent.getUser().getStatus())) {
            throw new ArmsAuthException("Agent not found or inactive", 400);
        }
        return agent;
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

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }
}
