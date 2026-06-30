package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.repositories.AdminRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;

    @Transactional
    public AgentResponse grantAgentAccess(String adminUserId) {
        var user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ArmsAuthException("User not found", 404));

        if (!isAdmin(user.getRoleCode())) {
            throw new ArmsAuthException("User is not an admin", 400);
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
                .orElseThrow(() -> new ArmsAuthException("User not found", 404));

        if (!isAdmin(user.getRoleCode())) {
            throw new ArmsAuthException("User is not an admin", 400);
        }

        var agent = agentRepository.findByUserId(adminUserId)
                .orElseThrow(() -> new ArmsAuthException("Admin has no agent access", 404));

        if (Boolean.TRUE.equals(agent.getStatus())) {
            agent.setStatus(false);
            agentRepository.save(agent);
        }

        return agentRepository.findByUserIdWithUser(adminUserId)
                .map(AgentResponse::from)
                .orElseThrow(() -> new ArmsAuthException("Agent record not found after revoke", 500));
    }

    public AgentResponse getAgentAccess(String adminUserId) {
        var user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ArmsAuthException("User not found", 404));

        if (!isAdmin(user.getRoleCode())) {
            throw new ArmsAuthException("User is not an admin", 400);
        }

        return agentRepository.findByUserIdWithUser(adminUserId)
                .map(AgentResponse::from)
                .orElseThrow(() -> new ArmsAuthException("Admin has no agent access", 404));
    }

    private boolean isAdmin(String roleCode) {
        return "ADMIN".equalsIgnoreCase(roleCode) || "SUPER_ADMIN".equalsIgnoreCase(roleCode);
    }
}
