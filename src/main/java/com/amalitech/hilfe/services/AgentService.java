package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import org.springframework.data.domain.PageImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentRepository agentRepository;
    private final DepartmentRepository departmentRepository;

    public Page<AgentResponse> listAgents(String departmentId, Pageable pageable) {
        if (departmentId == null || departmentId.isBlank()) {
            return agentRepository.findAllActiveWithUser(pageable).map(AgentResponse::from);
        }

        boolean activeDepartment = departmentRepository.findById(departmentId)
                .map(department -> Boolean.TRUE.equals(department.getStatus()))
                .orElse(false);
        if (!activeDepartment) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        return agentRepository.findByDepartmentIdWithUser(departmentId, pageable).map(AgentResponse::from);
    }

    public AgentResponse updateAvailability(String userId, boolean available) {
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ArmsAuthException("Agent not found", 404));
        agent.setStatus(available);
        Agent saved = agentRepository.save(agent);
        return AgentResponse.from(saved);
    }

    public AgentResponse updateAvailabilityById(String agentId, boolean available) {
        Agent agent = agentRepository.findByIdWithUser(agentId)
                .orElseThrow(() -> new ArmsAuthException("Agent not found", 404));
        agent.setStatus(available);
        Agent saved = agentRepository.save(agent);
        return AgentResponse.from(saved);
    }
}
