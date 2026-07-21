package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentService {
    private static final String AGENT_NOT_FOUND = "AGENT_NOT_FOUND";
    private static final String DEACTIVATED_ACCOUNT_MESSAGE = "Cannot update availability for a deactivated account.";
    private static final String SORT_FULL_NAME = "user.fullName";
    private static final String SORT_OFFICE_LOCATION = "user.location.name";
    private static final String SORT_STATUS = "status";
    private static final String SORT_CREATED_AT = "createdAt";
    private static final String SORT_UPDATED_AT = "updatedAt";
    private static final String SORT_LAST_ASSIGNED_AT = "lastAssignedAt";

    private static final Map<String, String> SORT_FIELD_ALIASES = Map.of(
            "fullName", SORT_FULL_NAME,
            "officeLocation", SORT_OFFICE_LOCATION
    );

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            SORT_FULL_NAME, SORT_OFFICE_LOCATION, SORT_STATUS, SORT_CREATED_AT, SORT_UPDATED_AT, SORT_LAST_ASSIGNED_AT
    );

    private final AgentRepository agentRepository;
    private final DepartmentRepository departmentRepository;

    public Page<AgentResponse> listAgents(String departmentId, String query, Boolean available, String locationId, Pageable pageable) {
        String queryPattern = buildQueryPattern(query);
        if (departmentId == null || departmentId.isBlank()) {
            return agentRepository.findAllActiveWithUserAndQuery(queryPattern, available, locationId, translateSort(pageable)).map(AgentResponse::from);
        }

        return getAgentResponses(departmentId, pageable, queryPattern, available, locationId);
    }

    @NonNull
    private Page<AgentResponse> getAgentResponses(String departmentId, Pageable pageable, String queryPattern, Boolean available, String locationId) {
        boolean activeDepartment = departmentRepository.findById(departmentId)
                .map(department -> Boolean.TRUE.equals(department.getStatus()))
                .orElse(false);
        if (!activeDepartment) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        return agentRepository.findByDepartmentIdWithUserAndQuery(departmentId, queryPattern, available, locationId, translateSort(pageable)).map(AgentResponse::from);
    }

    public Page<AgentResponse> listAllAgents(String departmentId, String query, Boolean available, String locationId, Pageable pageable) {
        String queryPattern = buildQueryPattern(query);
        if (departmentId == null || departmentId.isBlank()) {
            return agentRepository.findAllWithUserAndQuery(queryPattern, available, locationId, translateSort(pageable)).map(AgentResponse::from);
        }

        return getAgentResponses(departmentId, pageable, queryPattern, available, locationId);
    }

    private Pageable translateSort(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        List<Sort.Order> orders = pageable.getSort().stream()
                .map(o -> SORT_FIELD_ALIASES.containsKey(o.getProperty())
                        ? o.withProperty(SORT_FIELD_ALIASES.get(o.getProperty()))
                        : o)
                .filter(o -> ALLOWED_SORT_FIELDS.contains(o.getProperty()))
                .toList();
        Sort sort = orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
        return pageable.isUnpaged()
                ? Pageable.unpaged(sort)
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    public AgentResponse getStatus(String userId) {
        return AgentResponse.from(agentRepository.findByUserIdWithUser(userId)
                .orElseThrow(() -> new ArmsAuthException(AGENT_NOT_FOUND, 404)));
    }

    public AgentResponse updateAvailability(String userId, boolean available) {
        Agent agent = agentRepository.findByUserIdWithUser(userId)
                .orElseThrow(() -> new ArmsAuthException(AGENT_NOT_FOUND, 404));
        requireActiveAccount(agent);
        agent.setStatus(available);
        agentRepository.save(agent);
        return AgentResponse.from(agent);
    }

    public AgentResponse updateAvailabilityById(String agentId, boolean available) {
        Agent agent = agentRepository.findByIdWithUser(agentId)
                .orElseThrow(() -> new ArmsAuthException(AGENT_NOT_FOUND, 404));
        requireActiveAccount(agent);
        agent.setStatus(available);
        agentRepository.save(agent);
        return AgentResponse.from(agent);
    }

    private void requireActiveAccount(Agent agent) {
        if (!Boolean.TRUE.equals(agent.getUser().getStatus())) {
            throw new ArmsAuthException(DEACTIVATED_ACCOUNT_MESSAGE, 409);
        }
    }

    private String buildQueryPattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        return "%" + query.toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }
}
