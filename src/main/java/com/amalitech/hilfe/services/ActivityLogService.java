package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.ActivityLog;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.ActivityLogRepository;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final AgentRepository agentRepository;
    private final AgentGroupRepository agentGroupRepository;

    public Page<ActivityLogResponse> getActivityLogs(Pageable pageable) {
        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                );
        return activityLogRepository.findActivityLogResponses(sortedPageable);
    }

    public Page<ActivityLogResponse> getActivityLogs(String incidentId, Pageable pageable, String userId, String roleCode) {
        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                );

        if (incidentId == null) {
            if (!hasAuthority(RbacPermissions.RBAC_ROLE_READ)) {
                throw new ArmsAuthException("Access denied", 403);
            }
            return activityLogRepository.findActivityLogResponses(sortedPageable);
        }

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
        enforceIncidentAccess(userId, roleCode, incident);
        return activityLogRepository.findActivityLogResponsesByIncidentId(incidentId, sortedPageable);
    }

    private void enforceIncidentAccess(String userId, String roleCode, Incident incident) {
        if ("ADMIN".equalsIgnoreCase(roleCode) || "SUPER_ADMIN".equalsIgnoreCase(roleCode)) return;
        if (userId.equals(incident.getUserId())) return;
        if ("AGENT".equalsIgnoreCase(roleCode)) {
            boolean isAssignee = agentRepository.findByUserId(userId)
                    .map(a -> a.getId().equals(incident.getAssignedToId()))
                    .orElse(false);
            if (isAssignee) return;
        }
        throw new ArmsAuthException("You do not have access to this incident's activity log", 403);
    }

    private boolean hasAuthority(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission));
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserRoleChange(
            String actorUserId,
            String targetUserId,
            String previousRoleCode,
            String newRoleCode
    ) {
        try {
            String actorName = resolveUserName(actorUserId);
            String targetName = resolveUserName(targetUserId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .targetUserId(targetUserId)
                    .action("ROLE_CHANGED")
                    .subjectType("USER")
                    .subjectId(targetUserId)
                    .description(buildRoleChangeDescription(actorName, targetName, previousRoleCode, newRoleCode))
                    .metadata(buildRoleChangeMetadata(previousRoleCode, newRoleCode))
                    .build());
        } catch (RuntimeException exception) {
            log.error("Failed to persist activity log for role change on user {}", targetUserId, exception);
        }
    }

    public void logUserRoleChange(
            String actorUserId,
            String targetUserId,
            RoleCode previousRoleCode,
            RoleCode newRoleCode
    ) {
        logUserRoleChange(
                actorUserId,
                targetUserId,
                previousRoleCode == null ? null : previousRoleCode.name(),
                newRoleCode == null ? null : newRoleCode.name()
        );
    }

    private String buildRoleChangeDescription(String actorName, String targetName, String previousRoleCode, String newRoleCode) {
        return actorName + " changed role for " + targetName + " from " + formatRole(previousRoleCode)
                + " to " + formatRole(newRoleCode);
    }

    private String buildRoleChangeMetadata(String previousRoleCode, String newRoleCode) {
        return "{\"previousRoleCode\":\"" + formatRole(previousRoleCode)
                + "\",\"newRoleCode\":\"" + formatRole(newRoleCode) + "\"}";
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentStatusChange(String actorUserId, String incidentId, String previousStatus, String newStatus) {
        logIncidentStatusChange(actorUserId, incidentId, previousStatus, newStatus, null);
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentStatusChange(String actorUserId, String incidentId, String previousStatus, String newStatus, String reason) {
        try {
            String actorName = resolveUserName(actorUserId);
            String incidentLabel = resolveIncidentLabel(incidentId);
            String reasonPart = (reason != null && !reason.isBlank()) ? ". Reason: " + reason : "";
            String metadata = reason != null && !reason.isBlank()
                    ? "{\"previousStatus\":\"" + previousStatus + "\",\"newStatus\":\"" + newStatus + "\",\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}"
                    : "{\"previousStatus\":\"" + previousStatus + "\",\"newStatus\":\"" + newStatus + "\"}";
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INCIDENT_STATUS_CHANGED")
                    .subjectType("INCIDENT")
                    .subjectId(incidentId)
                    .description(incidentLabel + " status changed from " + previousStatus + " to " + newStatus + " by " + actorName + reasonPart)
                    .metadata(metadata)
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log status change for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentSeverityChange(String actorUserId, String incidentId, String previousSeverity, String newSeverity) {
        try {
            String actorName = resolveUserName(actorUserId);
            String incidentLabel = resolveIncidentLabel(incidentId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INCIDENT_SEVERITY_CHANGED")
                    .subjectType("INCIDENT")
                    .subjectId(incidentId)
                    .description(incidentLabel + " severity changed from " + previousSeverity + " to " + newSeverity + " by " + actorName)
                    .metadata("{\"previousSeverity\":\"" + previousSeverity + "\",\"newSeverity\":\"" + newSeverity + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log severity change for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentAssignment(String actorUserId, String incidentId, String agentId) {
        try {
            String actorName = resolveUserName(actorUserId);
            String incidentLabel = resolveIncidentLabel(incidentId);
            String agentName = resolveAgentName(agentId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INCIDENT_ASSIGNED")
                    .subjectType("INCIDENT")
                    .subjectId(incidentId)
                    .description(incidentLabel + " assigned to " + agentName + " by " + actorName)
                    .metadata("{\"agentId\":\"" + agentId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log assignment for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentAutoAssignment(String incidentId, String agentId) {
        try {
            String incidentLabel = resolveIncidentLabel(incidentId);
            String agentName = resolveAgentName(agentId);
            activityLogRepository.save(ActivityLog.builder()
                    .action("INCIDENT_ASSIGNED")
                    .subjectType("INCIDENT")
                    .subjectId(incidentId)
                    .description(incidentLabel + " auto-assigned to " + agentName + " by System")
                    .metadata("{\"agentId\":\"" + agentId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log auto-assignment for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentUnassignment(String actorUserId, String incidentId, String previousAgentId) {
        try {
            String actorName = resolveUserName(actorUserId);
            String incidentLabel = resolveIncidentLabel(incidentId);
            String agentName = resolveAgentName(previousAgentId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INCIDENT_UNASSIGNED")
                    .subjectType("INCIDENT")
                    .subjectId(incidentId)
                    .description(incidentLabel + " unassigned from " + agentName + " (agent inactive) by " + actorName)
                    .metadata("{\"previousAgentId\":\"" + previousAgentId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log unassignment for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAgentGroupStatusChange(String actorUserId, String agentGroupId, Boolean previousStatus, Boolean newStatus) {
        try {
            String actorName = resolveUserName(actorUserId);
            String groupName = resolveAgentGroupName(agentGroupId);
            String action = Boolean.TRUE.equals(newStatus) ? "AGENT_GROUP_ACTIVATED" : "AGENT_GROUP_DEACTIVATED";
            String verb = Boolean.TRUE.equals(newStatus) ? "activated" : "deactivated";
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action(action)
                    .subjectType("AGENT_GROUP")
                    .subjectId(agentGroupId)
                    .description(actorName + " " + verb + " agent group " + groupName)
                    .metadata("{\"previousStatus\":" + previousStatus + ",\"newStatus\":" + newStatus + "}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log status change for agent group {}", agentGroupId, ex);
        }
    }

    private String resolveAgentGroupName(String agentGroupId) {
        if (agentGroupId == null) return "Unknown";
        return agentGroupRepository.findById(agentGroupId)
                .map(g -> g.getName())
                .orElse("Unknown");
    }

    private String resolveUserName(String userId) {
        if (userId == null) return "Unknown";
        return userRepository.findById(userId)
                .map(u -> u.getFullName())
                .orElse("Unknown");
    }

    private String resolveIncidentLabel(String incidentId) {
        if (incidentId == null) return "Incident";
        return incidentRepository.findById(incidentId)
                .map(i -> "Incident #" + i.getIncidentNo())
                .orElse("Incident");
    }

    private String resolveAgentName(String agentId) {
        if (agentId == null) return "Unknown";
        return agentRepository.findByIdWithUser(agentId)
                .map(a -> a.getUser() != null ? a.getUser().getFullName() : "Unknown")
                .orElse("Unknown");
    }

    private String formatRole(String roleCode) {
        return roleCode == null ? "null" : roleCode;
    }
}
