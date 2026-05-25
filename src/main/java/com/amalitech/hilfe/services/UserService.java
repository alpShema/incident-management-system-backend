package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final ActivityLogService activityLogService;

    public Page<UserRoleSummaryResponse> getUsers(
            String query, RoleCode roleCode, String locationId, Boolean status,
            Pageable pageable
    ) {
        String queryPattern = null;
        if (query != null && !query.isBlank()) {
            String escaped = query.toLowerCase()
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            queryPattern = "%" + escaped + "%";
        }
        return userRepository.findUserRoleSummariesUnified(queryPattern, roleCode, locationId, status, pageable);
    }

    @Transactional
    public UserRoleSummaryResponse assignUserRole(String actorUserId, String userId, RoleCode roleCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ArmsAuthException("User not found", 404));

        RoleCode previousRoleCode = user.getRoleCode();
        user.setRoleCode(roleCode);
        ensureAgentRecord(user, roleCode);

        if (previousRoleCode != roleCode) {
            activityLogService.logUserRoleChange(actorUserId, userId, previousRoleCode, roleCode);
        }

        return new UserRoleSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getProfileImg(),
                user.getRoleCode(),
                user.getStatus(),
                user.getLocation() != null ? user.getLocation().getName() : null
        );
    }

    @Transactional
    public UserRoleSummaryResponse updateUserStatus(String actorUserId, RoleCode actorRole, String targetUserId, boolean status) {
        boolean isSelf = actorUserId.equals(targetUserId);
        boolean isAdmin = actorRole == RoleCode.ADMIN || actorRole == RoleCode.SUPER_ADMIN;

        if (!isSelf && !isAdmin) {
            throw new ArmsAuthException("You can only update your own status", 403);
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ArmsAuthException("User not found", 404));
        user.setStatus(status);
        userRepository.save(user);

        return new UserRoleSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getProfileImg(),
                user.getRoleCode(),
                user.getStatus(),
                user.getLocation() != null ? user.getLocation().getName() : null
        );
    }

    private void ensureAgentRecord(User user, RoleCode roleCode) {
        if (roleCode != RoleCode.AGENT || agentRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }

        agentRepository.save(Agent.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(user.getId())
                .status(true)
                .build());
    }
}
