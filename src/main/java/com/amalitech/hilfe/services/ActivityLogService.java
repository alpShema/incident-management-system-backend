package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.models.ActivityLog;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.ActivityLogRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
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
