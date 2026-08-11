package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final String USER_NOT_FOUND = "User not found";
    private static final String NOT_AN_ADMIN = "User is not an admin";
    private static final String NO_AGENT_ACCESS = "Admin has no agent access";

    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final IncidentService incidentService;

    @Transactional
    public AgentResponse grantAgentAccess(String adminUserId) {
        var user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ArmsAuthException(USER_NOT_FOUND, 404));

        if (!isAdmin(user.getRoleCode())) {
            throw new ArmsAuthException(NOT_AN_ADMIN, 400);
        }

        agentRepository.findByUserId(adminUserId).ifPresentOrElse(
                agent -> {
                    if (!Boolean.TRUE.equals(agent.getStatus())) {
                        agent.setStatus(true);
                        agentRepository.save(agent);
                    }
                },
                () -> agentRepository.save(Agent.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(adminUserId)
                        .status(true)
                        .build())
        );

        return agentRepository.findByUserIdWithUser(adminUserId)
                .map(AgentResponse::from)
                .orElseThrow(() -> new ArmsAuthException("Agent record not found after grant", 500));
    }

    @Transactional
    public AgentResponse revokeAgentAccess(String adminUserId) {
        var user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ArmsAuthException(USER_NOT_FOUND, 404));

        if (!isAdmin(user.getRoleCode())) {
            throw new ArmsAuthException(NOT_AN_ADMIN, 400);
        }

        var agent = agentRepository.findByUserId(adminUserId)
                .orElseThrow(() -> new ArmsAuthException(NO_AGENT_ACCESS, 404));

        if (Boolean.TRUE.equals(agent.getStatus())) {
            agent.setStatus(false);
            agentRepository.save(agent);
            incidentService.unassignAllForDeactivatedAgent(agent.getId(), null);
        }

        return agentRepository.findByUserIdWithUser(adminUserId)
                .map(AgentResponse::from)
                .orElseThrow(() -> new ArmsAuthException("Agent record not found after revoke", 500));
    }

    public AgentResponse getAgentAccess(String adminUserId) {
        var user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ArmsAuthException(USER_NOT_FOUND, 404));

        if (!isAdmin(user.getRoleCode())) {
            throw new ArmsAuthException(NOT_AN_ADMIN, 400);
        }

        return agentRepository.findByUserIdWithUser(adminUserId)
                .map(AgentResponse::from)
                .orElseThrow(() -> new ArmsAuthException(NO_AGENT_ACCESS, 404));
    }

    private boolean isAdmin(String roleCode) {
        return "ADMIN".equalsIgnoreCase(roleCode)
                || "ADMIN_AGENT".equalsIgnoreCase(roleCode)
                || "SUPER_ADMIN".equalsIgnoreCase(roleCode);
    }
}
