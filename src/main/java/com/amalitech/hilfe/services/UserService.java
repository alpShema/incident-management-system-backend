package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Role;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AdminRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.RoleRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final AdminRepository adminRepository;
    private final IncidentRepository incidentRepository;
    private final RoleRepository roleRepository;
    private final ActivityLogService activityLogService;

    public Page<UserRoleSummaryResponse> getUsers(
            String query, String roleCode, String locationId, Boolean status,
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
        Pageable resolvedPageable = remapSort(pageable);
        return userRepository.findUserRoleSummariesUnified(queryPattern, roleCode, locationId, status, resolvedPageable);
    }

    @Transactional
    public UserRoleSummaryResponse assignUserRole(String actorUserId, String userId, String roleCode) {
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        Role role = roleRepository.findByCode(normalizedRoleCode)
                .orElseThrow(() -> new ArmsAuthException("Role not found", 404));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ArmsAuthException("User not found", 404));

        String previousRoleCode = user.getRoleCode();
        user.setRoleCode(normalizedRoleCode);
        ensureAgentRecord(user, normalizedRoleCode);
        ensureAdminRecord(user, normalizedRoleCode);

        if (previousRoleCode == null || !previousRoleCode.equals(normalizedRoleCode)) {
            activityLogService.logUserRoleChange(actorUserId, userId, previousRoleCode, normalizedRoleCode);
        }

        return new UserRoleSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getProfileImg(),
                user.getRoleCode(),
                role.getName(),
                user.getStatus(),
                user.getLocation() != null ? user.getLocation().getName() : null,
                incidentRepository.countByUserId(user.getId()),
                getAssignedIncidentsCount(user.getId())
        );
    }

    public UserRoleSummaryResponse assignUserRole(String actorUserId, String userId, RoleCode roleCode) {
        return assignUserRole(actorUserId, userId, roleCode == null ? null : roleCode.name());
    }

    @Transactional
    public UserRoleSummaryResponse updateUserStatus(String actorUserId, RoleCode actorRole, String targetUserId, boolean status) {
        boolean isSelf = actorUserId.equals(targetUserId);
        boolean isAdmin = actorRole == RoleCode.ADMIN || actorRole == RoleCode.ADMIN_AGENT || actorRole == RoleCode.SUPER_ADMIN;

        if (!isSelf && !isAdmin) {
            throw new ArmsAuthException("You can only update your own status", 403);
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ArmsAuthException("User not found", 404));
        user.setStatus(status);
        userRepository.save(user);

        agentRepository.findByUserId(targetUserId).ifPresent(agent -> {
            agent.setStatus(status);
            agentRepository.save(agent);
        });

        return new UserRoleSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getProfileImg(),
                user.getRoleCode(),
                user.getRole() != null ? user.getRole().getName() : null,
                user.getStatus(),
                user.getLocation() != null ? user.getLocation().getName() : null,
                incidentRepository.countByUserId(user.getId()),
                getAssignedIncidentsCount(user.getId())
        );
    }

    private long getAssignedIncidentsCount(String userId) {
        return agentRepository.findByUserId(userId)
                .map(agent -> incidentRepository.countByAssignedToId(agent.getId()))
                .orElse(0L);
    }

    private Pageable remapSort(Pageable pageable) {
        Sort remapped = Sort.by(pageable.getSort().stream()
                .map(order -> {
                    if ("officeLocation".equals(order.getProperty())) {
                        return order.isAscending()
                                ? Sort.Order.asc("location.name")
                                : Sort.Order.desc("location.name");
                    }
                    if ("roleName".equals(order.getProperty())) {
                        return order.isAscending()
                                ? Sort.Order.asc("role.name")
                                : Sort.Order.desc("role.name");
                    }
                    return order;
                })
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), remapped);
    }

    private void ensureAdminRecord(User user, String roleCode) {
        boolean isAdminRole = "ADMIN".equalsIgnoreCase(roleCode) || "SUPER_ADMIN".equalsIgnoreCase(roleCode);
        if (!isAdminRole || adminRepository.findByUserIdWithUser(user.getId()).isPresent()) {
            return;
        }
        adminRepository.save(Admin.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(user.getId())
                .status(true)
                .build());
    }

    private void ensureAgentRecord(User user, String roleCode) {
        boolean needsAgentRecord = "AGENT".equalsIgnoreCase(roleCode) || "ADMIN_AGENT".equalsIgnoreCase(roleCode);
        if (!needsAgentRecord || agentRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }

        agentRepository.save(Agent.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(user.getId())
                .status(true)
                .build());
    }

    private String normalizeRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new ArmsAuthException("roleCode is required", 400);
        }
        return roleCode.trim().toUpperCase();
    }
}
