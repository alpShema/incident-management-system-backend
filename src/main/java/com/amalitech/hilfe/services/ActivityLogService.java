package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.models.ActivityLog;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.ActivityLogRepository;
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
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .targetUserId(targetUserId)
                    .action("ROLE_CHANGED")
                    .subjectType("USER")
                    .subjectId(targetUserId)
                    .description(buildRoleChangeDescription(targetUserId, previousRoleCode, newRoleCode))
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

    private String buildRoleChangeDescription(String targetUserId, String previousRoleCode, String newRoleCode) {
        return "Changed role for user " + targetUserId + " from " + formatRole(previousRoleCode)
                + " to " + formatRole(newRoleCode);
    }

    private String buildRoleChangeMetadata(String previousRoleCode, String newRoleCode) {
        return "{\"previousRoleCode\":\"" + formatRole(previousRoleCode)
                + "\",\"newRoleCode\":\"" + formatRole(newRoleCode) + "\"}";
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentStatusChange(String actorUserId, String incidentId, String previousStatus, String newStatus) {
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INCIDENT_STATUS_CHANGED")
                    .subjectType("INCIDENT")
                    .subjectId(incidentId)
                    .description("Incident " + incidentId + " status changed from " + previousStatus + " to " + newStatus)
                    .metadata("{\"previousStatus\":\"" + previousStatus + "\",\"newStatus\":\"" + newStatus + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log status change for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentSeverityChange(String actorUserId, String incidentId, String previousSeverity, String newSeverity) {
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INCIDENT_SEVERITY_CHANGED")
                    .subjectType("INCIDENT")
                    .subjectId(incidentId)
                    .description("Incident " + incidentId + " severity changed from " + previousSeverity + " to " + newSeverity)
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
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INCIDENT_ASSIGNED")
                    .subjectType("INCIDENT")
                    .subjectId(incidentId)
                    .description("Incident " + incidentId + " assigned to agent " + agentId)
                    .metadata("{\"agentId\":\"" + agentId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log assignment for incident {}", incidentId, ex);
        }
    }

    private String formatRole(String roleCode) {
        return roleCode == null ? "null" : roleCode;
    }
}
