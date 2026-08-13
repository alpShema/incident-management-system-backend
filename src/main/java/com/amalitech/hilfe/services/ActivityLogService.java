package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.ActivityLog;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.Role;
import com.amalitech.hilfe.repositories.ActivityLogRepository;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.RoleRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.security.authorization.CurrentUserAuthority;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import com.amalitech.hilfe.models.User;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogService {
    private static final String SUBJECT_INCIDENT = "INCIDENT";
    private static final String UNKNOWN = "Unknown";
    private static final String SYSTEM = "System";
    private static final String NOTE_ID_META_PREFIX = "{\"noteId\":\"";
    private static final String AS_HEAD_OF_DEPARTMENT = " as head of department ";
    private static final String ACTION_INCIDENT_VIEWED = "INCIDENT_VIEWED";
    private static final String ACTION_UNAUTHORIZED_CONFIDENTIAL_ACCESS = "UNAUTHORIZED_CONFIDENTIAL_ACCESS";

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final AgentRepository agentRepository;
    private final AgentGroupRepository agentGroupRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final ConfidentialIncidentAccess confidentialIncidentAccess;

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

        // findByIdWithDetails (not findById) -- enforceIncidentAccess reads incident.getIncidentType()
        // for the confidential check below, and this method isn't @Transactional (open-in-view is
        // off), so a lazily-fetched incidentType would blow up with LazyInitializationException.
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
        enforceIncidentAccess(userId, roleCode, incident);
        // Clients aren't shown staff "Viewed" activity — it's an internal detail, not something
        // they were ever meant to audit.
        if ("CLIENT".equalsIgnoreCase(roleCode)) {
            return activityLogRepository.findActivityLogResponsesByIncidentIdExcludingAction(
                    incidentId, ACTION_INCIDENT_VIEWED, sortedPageable);
        }
        return activityLogRepository.findActivityLogResponsesByIncidentId(incidentId, sortedPageable);
    }

    private void enforceIncidentAccess(String userId, String roleCode, Incident incident) {
        // HV-1619: a confidential incident's history is just as sensitive as the incident
        // itself -- assignment/severity/status entries would otherwise leak exactly what the
        // masked list views and blocked detail view are hiding. Same predicate, same override
        // of the admin bypass below; this surface doesn't add its own history entry on denial
        // (no dedicated "viewed the log" concept exists here, unlike logIncidentViewed).
        IncidentType topic = incident.getIncidentType();
        if (topic != null && topic.isConfidential()) {
            if (confidentialIncidentAccess.canAccess(userId, incident)) {
                return;
            }
            throw new ArmsAuthException("You do not have access to this incident's activity log", 403);
        }
        if ("ADMIN".equalsIgnoreCase(roleCode) || "ADMIN_AGENT".equalsIgnoreCase(roleCode) || "SUPER_ADMIN".equalsIgnoreCase(roleCode)) return;
        if (userId.equals(incident.getUserId())) return;
        if ("AGENT".equalsIgnoreCase(roleCode)) {
            boolean isAssignee = agentRepository.findByUserId(userId)
                    .map(a -> a.getId().equals(incident.getAssignedToId()))
                    .orElse(false);
            if (isAssignee) return;
            if (isSameDepartmentAsAssignedAgent(userId, incident)) return;
        }
        throw new ArmsAuthException("You do not have access to this incident's activity log", 403);
    }

    private Optional<List<String>> findAgentGroupIds(String userId) {
        return agentRepository.findByUserId(userId)
                .map(agent -> agentGroupMemberRepository.findAgentGroupIdsByAgentId(agent.getId()));
    }

    private boolean isSameDepartmentAsAssignedAgent(String userId, Incident incident) {
        if (incident.getAssignedToId() == null) return false;
        List<String> actorGroupIds = findAgentGroupIds(userId).orElse(List.of());
        if (actorGroupIds.isEmpty()) return false;
        List<String> assignedGroupIds = agentGroupMemberRepository.findAgentGroupIdsByAgentId(incident.getAssignedToId());
        if (assignedGroupIds.isEmpty()) return false;
        List<String> actorDeptIds = agentGroupRepository.findDepartmentIdsByGroupIds(actorGroupIds);
        if (actorDeptIds.isEmpty()) {
            return assignedGroupIds.stream().anyMatch(actorGroupIds::contains);
        }
        List<String> assignedDeptIds = agentGroupRepository.findDepartmentIdsByGroupIds(assignedGroupIds);
        return assignedDeptIds.stream().anyMatch(actorDeptIds::contains);
    }

    private boolean hasAuthority(String permission) {
        return CurrentUserAuthority.has(permission);
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserRoleChange(
            String actorUserId,
            String targetUserId,
            String previousRoleCode,
            String newRoleCode
    ) {
        doLogUserRoleChange(actorUserId, targetUserId, previousRoleCode, newRoleCode);
    }

    public void logUserRoleChange(
            String actorUserId,
            String targetUserId,
            RoleCode previousRoleCode,
            RoleCode newRoleCode
    ) {
        doLogUserRoleChange(
                actorUserId,
                targetUserId,
                previousRoleCode == null ? null : previousRoleCode.name(),
                newRoleCode == null ? null : newRoleCode.name()
        );
    }

    private void doLogUserRoleChange(String actorUserId, String targetUserId, String previousRoleCode, String newRoleCode) {
        try {
            String actorName = resolveUserName(actorUserId);
            String targetName = resolveUserName(targetUserId);
            String previousRoleName = resolveRoleName(previousRoleCode);
            String newRoleName = resolveRoleName(newRoleCode);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .targetUserId(targetUserId)
                    .action("ROLE_CHANGED")
                    .subjectType("USER")
                    .subjectId(targetUserId)
                    .description(buildRoleChangeDescription(actorName, targetName, previousRoleName, newRoleName))
                    .metadata(buildRoleChangeMetadata(previousRoleCode, newRoleCode))
                    .build());
        } catch (RuntimeException exception) {
            log.error("Failed to persist activity log for role change on user {}", targetUserId, exception);
        }
    }

    private String buildRoleChangeDescription(String actorName, String targetName, String previousRoleName, String newRoleName) {
        return actorName + " changed role for " + targetName + " from " + previousRoleName
                + " to " + newRoleName;
    }

    private String buildRoleChangeMetadata(String previousRoleCode, String newRoleCode) {
        return "{\"previousRoleCode\":\"" + (previousRoleCode == null ? "null" : previousRoleCode)
                + "\",\"newRoleCode\":\"" + (newRoleCode == null ? "null" : newRoleCode) + "\"}";
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentStatusChange(String actorUserId, String incidentId, String previousStatus, String newStatus) {
        doLogIncidentStatusChange(actorUserId, incidentId, previousStatus, newStatus, null);
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIncidentStatusChange(String actorUserId, String incidentId, String previousStatus, String newStatus, String reason) {
        doLogIncidentStatusChange(actorUserId, incidentId, previousStatus, newStatus, reason);
    }

    private void doLogIncidentStatusChange(String actorUserId, String incidentId, String previousStatus, String newStatus, String reason) {
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
                    .subjectType(SUBJECT_INCIDENT)
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
    public void logIncidentViewed(String actorUserId, String incidentId) {
        try {
            String actorName = resolveUserName(actorUserId);
            String incidentLabel = resolveIncidentLabel(incidentId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action(ACTION_INCIDENT_VIEWED)
                    .subjectType(SUBJECT_INCIDENT)
                    .subjectId(incidentId)
                    .description(incidentLabel + " viewed by " + actorName)
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log view for incident {}", incidentId, ex);
        }
    }

    // HV-1619: an agent/admin outside a confidential topic's linked agent group tried to open
    // the incident directly and was denied. Recorded on its own -- separate from
    // logIncidentViewed, which only fires on a successful view -- since this is exactly the
    // "attempt recorded in incident history" acceptance criterion.
    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUnauthorizedConfidentialAccess(String actorUserId, String incidentId) {
        try {
            String actorName = resolveUserName(actorUserId);
            String incidentLabel = resolveIncidentLabel(incidentId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action(ACTION_UNAUTHORIZED_CONFIDENTIAL_ACCESS)
                    .subjectType(SUBJECT_INCIDENT)
                    .subjectId(incidentId)
                    .description(actorName + " attempted to view confidential " + incidentLabel + " without authorization")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log unauthorized confidential access attempt for incident {}", incidentId, ex);
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
                    .subjectType(SUBJECT_INCIDENT)
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
                    .subjectType(SUBJECT_INCIDENT)
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
                    .subjectType(SUBJECT_INCIDENT)
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
                    .subjectType(SUBJECT_INCIDENT)
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
    public void logIncidentSlaBreached(String incidentId, String slaType, long minutesOverdue) {
        try {
            String incidentLabel = resolveIncidentLabel(incidentId);
            activityLogRepository.save(ActivityLog.builder()
                    .action("INCIDENT_SLA_BREACHED")
                    .subjectType(SUBJECT_INCIDENT)
                    .subjectId(incidentId)
                    .description(incidentLabel + " " + slaType.toLowerCase() + " SLA breached by " + minutesOverdue + " minute(s)")
                    .metadata("{\"slaType\":\"" + slaType + "\",\"minutesOverdue\":" + minutesOverdue + "}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log SLA breach for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSelfAssignmentPrevented(String incidentId, String creatorAgentId, String assignedAgentId) {
        try {
            String incidentLabel = resolveIncidentLabel(incidentId);
            String assignedName = resolveAgentName(assignedAgentId);
            activityLogRepository.save(ActivityLog.builder()
                    .action("SELF_ASSIGNMENT_PREVENTED")
                    .subjectType(SUBJECT_INCIDENT)
                    .subjectId(incidentId)
                    .description(incidentLabel + " self-assignment prevented; routed to " + assignedName + " instead")
                    .metadata("{\"creatorAgentId\":\"" + creatorAgentId + "\",\"assignedAgentId\":\"" + assignedAgentId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log self-assignment prevention for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSelfAssignmentEscalated(String incidentId, String creatorAgentId) {
        try {
            String incidentLabel = resolveIncidentLabel(incidentId);
            activityLogRepository.save(ActivityLog.builder()
                    .action("SELF_ASSIGNMENT_ESCALATED")
                    .subjectType(SUBJECT_INCIDENT)
                    .subjectId(incidentId)
                    .description(incidentLabel + " escalated to admin — no available agent other than the creator")
                    .metadata("{\"creatorAgentId\":\"" + creatorAgentId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log self-assignment escalation for incident {}", incidentId, ex);
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

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDepartmentHeadAssigned(String actorUserId, String departmentId, String previousHeadUserId, String newHeadUserId) {
        try {
            String actorName = resolveUserName(actorUserId);
            String departmentName = resolveDepartmentName(departmentId);
            String newHeadName = resolveUserName(newHeadUserId);
            String description = previousHeadUserId == null
                    ? actorName + " assigned " + newHeadName + AS_HEAD_OF_DEPARTMENT + departmentName
                    : actorName + " replaced " + resolveUserName(previousHeadUserId) + " with " + newHeadName
                            + AS_HEAD_OF_DEPARTMENT + departmentName;
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .targetUserId(newHeadUserId)
                    .action("DEPARTMENT_HEAD_ASSIGNED")
                    .subjectType("DEPARTMENT")
                    .subjectId(departmentId)
                    .description(description)
                    .metadata("{\"previousHeadUserId\":" + (previousHeadUserId == null ? "null" : "\"" + previousHeadUserId + "\"")
                            + ",\"newHeadUserId\":\"" + newHeadUserId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log department head assignment for department {}", departmentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDepartmentHeadRemoved(String actorUserId, String departmentId, String previousHeadUserId) {
        try {
            String actorName = resolveUserName(actorUserId);
            String departmentName = resolveDepartmentName(departmentId);
            String previousHeadName = resolveUserName(previousHeadUserId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .targetUserId(previousHeadUserId)
                    .action("DEPARTMENT_HEAD_REMOVED")
                    .subjectType("DEPARTMENT")
                    .subjectId(departmentId)
                    .description(actorName + " removed " + previousHeadName + AS_HEAD_OF_DEPARTMENT + departmentName)
                    .metadata("{\"previousHeadUserId\":\"" + previousHeadUserId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log department head removal for department {}", departmentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInternalNoteCreated(String actorUserId, String incidentId, String noteId) {
        try {
            String actorName = resolveUserName(actorUserId);
            String incidentLabel = resolveIncidentLabel(incidentId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INTERNAL_NOTE_CREATED")
                    .subjectType(SUBJECT_INCIDENT)
                    .subjectId(incidentId)
                    .description(actorName + " added an internal note on " + incidentLabel)
                    .metadata(NOTE_ID_META_PREFIX + noteId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log internal note creation for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInternalNoteUpdated(String actorUserId, String incidentId, String noteId) {
        try {
            String actorName = resolveUserName(actorUserId);
            String incidentLabel = resolveIncidentLabel(incidentId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INTERNAL_NOTE_UPDATED")
                    .subjectType(SUBJECT_INCIDENT)
                    .subjectId(incidentId)
                    .description(actorName + " updated an internal note on " + incidentLabel)
                    .metadata(NOTE_ID_META_PREFIX + noteId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log internal note update for incident {}", incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInternalNoteDeleted(String actorUserId, String incidentId, String noteId) {
        try {
            String actorName = resolveUserName(actorUserId);
            String incidentLabel = resolveIncidentLabel(incidentId);
            activityLogRepository.save(ActivityLog.builder()
                    .actorUserId(actorUserId)
                    .action("INTERNAL_NOTE_DELETED")
                    .subjectType(SUBJECT_INCIDENT)
                    .subjectId(incidentId)
                    .description(actorName + " deleted an internal note on " + incidentLabel)
                    .metadata(NOTE_ID_META_PREFIX + noteId + "\"}")
                    .build());
        } catch (RuntimeException ex) {
            log.error("Failed to log internal note deletion for incident {}", incidentId, ex);
        }
    }

    private String resolveAgentGroupName(String agentGroupId) {
        if (agentGroupId == null) return UNKNOWN;
        return agentGroupRepository.findById(agentGroupId)
                .map(g -> g.getName())
                .orElse(UNKNOWN);
    }

    private String resolveDepartmentName(String departmentId) {
        if (departmentId == null) return UNKNOWN;
        return departmentRepository.findById(departmentId)
                .map(d -> d.getName())
                .orElse(UNKNOWN);
    }

    private String resolveUserName(String userId) {
        if (userId == null) return SYSTEM;
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse(UNKNOWN);
    }

    private String resolveIncidentLabel(String incidentId) {
        if (incidentId == null) return "Incident";
        return incidentRepository.findById(incidentId)
                .map(i -> "Incident #" + i.getIncidentNo())
                .orElse("Incident");
    }

    private String resolveAgentName(String agentId) {
        if (agentId == null) return UNKNOWN;
        return agentRepository.findByIdWithUser(agentId)
                .map(a -> a.getUser() != null ? a.getUser().getFullName() : UNKNOWN)
                .orElse(UNKNOWN);
    }

    private String resolveRoleName(String roleCode) {
        if (roleCode == null) return "null";
        return roleRepository.findByCode(roleCode)
                .map(Role::getName)
                .orElse(roleCode);
    }
}
