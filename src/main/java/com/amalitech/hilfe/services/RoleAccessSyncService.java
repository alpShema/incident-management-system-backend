package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AdminRepository;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Single source of truth for keeping the {@link Agent}/{@link Admin} shadow records
 * in sync with {@code User.roleCode}. Every code path that changes a user's role
 * (single-user update, bulk assign, bulk removal) must route through this class so
 * a demoted user is consistently deactivated everywhere instead of drifting per-path.
 */
@Service
@RequiredArgsConstructor
public class RoleAccessSyncService {
    private final AgentRepository agentRepository;
    private final AdminRepository adminRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;

    public void syncAgentRecord(User user, String roleCode) {
        boolean needsAgentRecord = "AGENT".equalsIgnoreCase(roleCode) || "ADMIN_AGENT".equalsIgnoreCase(roleCode);
        var existingAgent = agentRepository.findByUserId(user.getId());

        if (needsAgentRecord) {
            if (existingAgent.isPresent()) {
                Agent agent = existingAgent.get();
                if (!Boolean.TRUE.equals(agent.getStatus())) {
                    agent.setStatus(true);
                    agentRepository.save(agent);
                }
                return;
            }
            agentRepository.save(Agent.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(user.getId())
                    .status(true)
                    .build());
            return;
        }

        existingAgent.ifPresent(agent -> {
            if (Boolean.TRUE.equals(agent.getStatus())) {
                agent.setStatus(false);
                agentRepository.save(agent);
            }
            agentGroupMemberRepository.deleteByAgentId(agent.getId());
        });
    }

    public void syncAdminRecord(User user, String roleCode) {
        boolean isAdminRole = "ADMIN".equalsIgnoreCase(roleCode) || "SUPER_ADMIN".equalsIgnoreCase(roleCode);
        var existingAdmin = adminRepository.findByUserIdWithUser(user.getId());

        if (isAdminRole) {
            if (existingAdmin.isPresent()) {
                Admin admin = existingAdmin.get();
                if (!admin.isStatus()) {
                    admin.setStatus(true);
                    adminRepository.save(admin);
                }
                return;
            }
            adminRepository.save(Admin.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(user.getId())
                    .status(true)
                    .build());
            return;
        }

        existingAdmin.ifPresent(admin -> {
            if (admin.isStatus()) {
                admin.setStatus(false);
                adminRepository.save(admin);
            }
        });
    }
}
