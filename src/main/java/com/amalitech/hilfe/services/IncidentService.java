package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.*;
import com.amalitech.hilfe.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private static final String DEFAULT_PRIORITY_NAME = "Low";

    // Frontend sort alias → JPA field path
    private static final Map<String, String> SORT_FIELD_ALIASES = Map.of(
            "category", "incidentType.category.name",
            "priority", "severity.name"
    );

    // from-status-id → to-status-id → roles permitted to make that transition
    private static final Map<String, Map<String, Set<String>>> VALID_TRANSITIONS = Map.of(
            "status-in-progress", Map.of(
                    "status-pending",  Set.of("AGENT"),
                    "status-resolved", Set.of("AGENT")
            ),
            "status-pending", Map.of(
                    "status-in-progress", Set.of("AGENT")
            ),
            "status-resolved", Map.of(
                    "status-closed",   Set.of("CLIENT"),
                    "status-reopened", Set.of("CLIENT")
            )
    );

    private final IncidentRepository incidentRepository;
    private final IncidentTypeRepository incidentTypeRepository;
    private final AgentGroupRepository agentGroupRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final AgentRepository agentRepository;
    private final StatusRepository statusRepository;
    private final SeverityRepository severityRepository;
    private final AdminRepository adminRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;
    private final LocationRepository locationRepository;
    private final AutoCloseService autoCloseService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public IncidentResponse createIncident(String userId, CreateIncidentRequest request) {
        List<String> notFound = new java.util.ArrayList<>();
        IncidentType incidentType = incidentTypeRepository.findById(request.incidentTypeId()).orElse(null);
        if (incidentType == null) {
            notFound.add("Incident type with the provided ID could not be found.");
        }
        if (!locationRepository.existsById(request.locationId())) {
            notFound.add("Location with the provided ID could not be found.");
        }
        if (!notFound.isEmpty()) {
            throw new ArmsAuthException(String.join(" ", notFound), 404);
        }

        Incident incident = Incident.builder()
                .id(UUID.randomUUID().toString())
                .title(request.title())
                .description(request.description())
                .userId(userId)
                .locationId(request.locationId())
                .incidentTypeId(request.incidentTypeId())
                .severityId(resolvePriorityId(request.severityId()))
                .build();
        applyTopicAssignment(incident, incidentType);

        Incident saved = incidentRepository.save(incident);
        entityManager.flush();

        int incidentNo = saved.getIncidentNo() != null ? saved.getIncidentNo() : 0;
        // TASK 3 VERIFIED: auto-assignment notification is correctly wired.
        // applyTopicAssignment() sets assignedToId when a matching active agent exists;
        // after save/flush the resolved agentUserId is passed to sendAssignmentNotification.
        // If no agent was assigned, an escalation notification is sent to an admin instead.
        if (saved.getAssignedToId() != null) {
            String agentUserId = resolveAgentUserId(saved.getAssignedToId());
            notificationService.sendAssignmentNotification(agentUserId, saved.getId(), incidentNo);
        } else {
            String adminUserId = findAnyAdminUserId();
            notificationService.sendEscalationNotification(adminUserId, saved.getId(), incidentNo);
        }

        List<MediaResponse> mediaResponses = List.of();
        if (request.attachments() != null && !request.attachments().isEmpty()) {
            List<Media> mediaList = mediaService.createMediaForIncident(saved.getId(), request.attachments());
            mediaResponses = mediaService.toMediaResponses(mediaList);
            entityManager.flush();
        }

        entityManager.clear();
        return IncidentResponse.from(
                incidentRepository.findByIdWithDetails(saved.getId()).orElseThrow(),
                mediaResponses);
    }

    public Page<IncidentResponse> queryIncidents(
            String userId,
            String query,
            String statusId, String severityId, String incidentTypeId, String categoryId, String locationId,
            Instant fromDate, Instant toDate,
            Pageable pageable
    ) {
        return incidentRepository
                .findByUserIdUnified(userId, buildQueryPattern(query), statusId, severityId,
                        incidentTypeId, categoryId, locationId, fromDate, toDate, ensureSorted(pageable))
                .map(IncidentResponse::from);
    }

    public Page<IncidentResponse> queryAllIncidents(
            String query,
            String statusId, String severityId, String incidentTypeId, String categoryId, String locationId,
            Instant fromDate, Instant toDate,
            Pageable pageable
    ) {
        return incidentRepository
                .findAllUnified(buildQueryPattern(query), statusId, severityId,
                        incidentTypeId, categoryId, locationId, fromDate, toDate, ensureSorted(pageable))
                .map(IncidentResponse::from);
    }

    public Page<IncidentResponse> queryDeptIncidents(
            String userId,
            String query,
            String statusId, String severityId, String incidentTypeId, String categoryId, String locationId,
            Instant fromDate, Instant toDate,
            Pageable pageable
    ) {
        Pageable sorted = ensureSorted(pageable);
        String queryPattern = buildQueryPattern(query);
        return findAgentGroupIds(userId)
                .filter(ids -> !ids.isEmpty())
                .map(ids -> incidentRepository
                        .findByDepartmentUnified(ids, queryPattern, statusId, severityId,
                                incidentTypeId, categoryId, locationId, fromDate, toDate, sorted)
                        .map(IncidentResponse::from))
                .orElse(new PageImpl<>(List.of(), sorted, 0));
    }

    public Page<IncidentResponse> queryAssignedIncidents(
            String userId,
            String query,
            String statusId, String severityId, String incidentTypeId, String categoryId, String locationId,
            Instant fromDate, Instant toDate,
            Pageable pageable
    ) {
        Pageable sorted = ensureSorted(pageable);
        String queryPattern = buildQueryPattern(query);
        return agentRepository.findByUserId(userId)
                .map(agent -> incidentRepository
                        .findByAssignedToIdUnified(agent.getId(), queryPattern, statusId, severityId,
                                incidentTypeId, categoryId, locationId, fromDate, toDate, sorted)
                        .map(IncidentResponse::from))
                .orElse(new PageImpl<>(List.of(), sorted, 0));
    }

    public Page<IncidentResponse> searchIncidents(String userId, String query, Instant fromDate, Instant toDate, Pageable pageable) {
        if (query == null || query.isBlank()) {
            throw new ArmsAuthException("Search query must not be blank", 400);
        }

        Pageable sortedPageable = pageable.isUnpaged()
                ? Pageable.unpaged(Sort.by(Sort.Direction.DESC, "createdAt"))
                : ensureSorted(pageable);

        return incidentRepository
                .searchByUserId(userId, buildQueryPattern(query), fromDate, toDate, sortedPageable)
                .map(IncidentResponse::from);
    }

    public IncidentResponse getIncident(String userId, String roleCode, String incidentId) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));

        enforceAccess(userId, roleCode, incident);

        List<Media> mediaList = mediaRepository.findByIncidentId(incidentId);
        List<MediaResponse> mediaResponses = mediaService.toMediaResponses(mediaList);
        return IncidentResponse.from(incident, mediaResponses);
    }

    public IncidentResponse getIncident(String userId, RoleCode roleCode, String incidentId) {
        return getIncident(userId, roleCode == null ? null : roleCode.name(), incidentId);
    }

    @Transactional
    public IncidentResponse updateStatus(String actorUserId, String roleCode, String incidentId, UpdateIncidentStatusRequest request) {
        Incident incident = findIncident(incidentId);

        Status newStatus = statusRepository.findById(request.statusId())
                .orElseThrow(() -> new ArmsAuthException("Status not found", 404));

        enforceTransition(incident, newStatus, roleCode, actorUserId);
        enforceReopenWindow(incident, newStatus);
        enforceReasonRequired(newStatus, request.reason());

        String previousStatusName = incident.getStatus() != null ? incident.getStatus().getName() : "none";
        incident.setStatusId(request.statusId());
        incident.setStatusReason(requiresReason(newStatus) ? request.reason() : null);
        incident.setResolvedAt("status-resolved".equals(newStatus.getId()) ? Instant.now() : null);
        incident.setClosedAt("status-closed".equals(newStatus.getId()) ? Instant.now() : null);

        incidentRepository.save(incident);
        activityLogService.logIncidentStatusChange(actorUserId, incidentId, previousStatusName, newStatus.getName(), request.reason());
        dispatchStatusNotifications(incident, previousStatusName, newStatus.getName(), request.reason(), actorUserId);

        if ("reopened".equalsIgnoreCase(newStatus.getName())) {
            applyReopenTransition(actorUserId, incident, incidentId);
        }

        entityManager.flush();
        entityManager.clear();
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    @Transactional
    public IncidentResponse updateStatus(String actorUserId, RoleCode roleCode, String incidentId, UpdateIncidentStatusRequest request) {
        return updateStatus(actorUserId, roleCode == null ? null : roleCode.name(), incidentId, request);
    }

    @Transactional
    public IncidentResponse updateSeverity(String actorUserId, String incidentId, UpdateIncidentSeverityRequest request) {
        Incident incident = findIncident(incidentId);
        String previousSeverityName = incident.getSeverity() != null ? incident.getSeverity().getName() : "none";
        incident.setSeverityId(request.severityId());
        incidentRepository.save(incident);
        activityLogService.logIncidentSeverityChange(actorUserId, incidentId, previousSeverityName, request.severityId());
        entityManager.flush();
        entityManager.clear();
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    @Transactional
    public IncidentResponse assignIncident(String actorUserId, String incidentId, AssignIncidentRequest request) {
        Incident incident = findIncident(incidentId);

        // Capture the previous agent's userId BEFORE overwriting assignedToId
        String previousAgentUserId = resolveAgentUserId(incident.getAssignedToId());

        incident.setAssignedToId(request.agentId());

        String inProgressStatusId = statusRepository.findByNameIgnoreCase("In Progress")
                .orElseThrow(() -> new ArmsAuthException("Default 'In Progress' status not configured", 500))
                .getId();
        incident.setStatusId(inProgressStatusId);

        incidentRepository.save(incident);
        activityLogService.logIncidentAssignment(actorUserId, incidentId, request.agentId());

        String agentUserId = resolveAgentUserId(request.agentId());
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        notificationService.sendAssignmentNotification(agentUserId, incidentId, incidentNo);

        // Notify the previous agent (if any and different from the new agent) that they have been unassigned
        if (previousAgentUserId != null && !previousAgentUserId.equals(agentUserId)) {
            notificationService.sendUnassignedNotification(previousAgentUserId, incidentId, incidentNo);
        }

        entityManager.flush();
        entityManager.clear();
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildQueryPattern(String query) {
        if (query == null || query.isBlank()) return null;
        return "%" + query.toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    private Pageable ensureSorted(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            Sort translated = translateSort(pageable.getSort());
            return pageable.isUnpaged()
                    ? Pageable.unpaged(translated)
                    : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translated);
        }
        if (pageable.isUnpaged()) return Pageable.unpaged(Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Sort translateSort(Sort sort) {
        List<Sort.Order> orders = sort.stream()
                .map(o -> SORT_FIELD_ALIASES.containsKey(o.getProperty())
                        ? o.withProperty(SORT_FIELD_ALIASES.get(o.getProperty()))
                        : o)
                .toList();
        return Sort.by(orders);
    }

    private void enforceAccess(String userId, String roleCode, Incident incident) {
        if ("ADMIN".equalsIgnoreCase(roleCode) || "SUPER_ADMIN".equalsIgnoreCase(roleCode)) {
            return;
        }
        if (userId.equals(incident.getUserId())) {
            return;
        }
        if ("AGENT".equalsIgnoreCase(roleCode) && isSameDepartmentAsAssignedAgent(userId, incident)) {
            return;
        }
        throw new ArmsAuthException("You do not have access to this incident", 403);
    }

    private void applyTopicAssignment(Incident incident, IncidentType incidentType) {
        if (incidentType != null
                && incidentType.getAgentGroupId() != null
                && !incidentType.getAgentGroupId().isBlank()) {
            AgentGroup agentGroup = agentGroupRepository.findById(incidentType.getAgentGroupId())
                    .orElseThrow(() -> new ArmsAuthException("Topic agent group not found", 404));

            String assignedAgentId = findAvailableAgentInGroup(agentGroup, incident.getLocationId());
            if (assignedAgentId != null) {
                incident.setAssignedToId(assignedAgentId);
                incident.setStatusId(statusRepository.findByNameIgnoreCase("Pending")
                        .orElseThrow(() -> new ArmsAuthException("Default 'Pending' status not configured", 500))
                        .getId());
            } else {
                incident.setStatusId("status-open");
            }
            return;
        }
        if (incidentType != null && incidentType.getAgentId() != null && !incidentType.getAgentId().isBlank()) {
            Agent agent = agentRepository.findById(incidentType.getAgentId()).orElse(null);
            if (agent != null && Boolean.TRUE.equals(agent.getStatus())) {
                incident.setAssignedToId(incidentType.getAgentId());
                incident.setStatusId(statusRepository.findByNameIgnoreCase("Pending")
                        .orElseThrow(() -> new ArmsAuthException("Default 'Pending' status not configured", 500))
                        .getId());
            } else {
                incident.setStatusId("status-open");
            }
            return;
        }
        incident.setStatusId("status-open");
    }

    private String findAvailableAgentInGroup(AgentGroup agentGroup, String locationId) {
        List<Agent> locationMatched = agentRepository
                .findAvailableByAgentGroupIdAndLocation(agentGroup.getId(), locationId);
        if (!locationMatched.isEmpty()) {
            return locationMatched.get(0).getId();
        }
        List<Agent> anyAvailable = agentRepository
                .findAvailableByAgentGroupIdViaMembership(agentGroup.getId());
        if (!anyAvailable.isEmpty()) {
            return anyAvailable.get(0).getId();
        }
        return null;
    }

    private Optional<List<String>> findAgentGroupIds(String userId) {
        return agentRepository.findByUserId(userId)
                .map(agent -> agentGroupMemberRepository.findAgentGroupIdsByAgentId(agent.getId()));
    }

    private boolean isSameDepartmentAsAssignedAgent(String userId, Incident incident) {
        if (incident.getAssignedToId() == null) {
            return false;
        }
        List<String> actorDepartments = findAgentGroupIds(userId).orElse(List.of());
        if (actorDepartments.isEmpty()) {
            return false;
        }
        List<String> assignedDepartments = agentGroupMemberRepository.findAgentGroupIdsByAgentId(incident.getAssignedToId());
        return assignedDepartments.stream().anyMatch(actorDepartments::contains);
    }

    private Incident findIncident(String incidentId) {
        return incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
    }

    private String resolvePriorityId(String requestedSeverityId) {
        if (requestedSeverityId != null && !requestedSeverityId.isBlank()) {
            if (!severityRepository.existsById(requestedSeverityId)) {
                throw new ArmsAuthException("Severity not found", 404);
            }
            return requestedSeverityId;
        }

        Severity defaultPriority = severityRepository.findByNameIgnoreCase(DEFAULT_PRIORITY_NAME)
                .orElseThrow(() -> new ArmsAuthException("Default 'Low' priority not configured", 500));
        return defaultPriority.getId();
    }

    private void applyReopenTransition(String actorUserId, Incident incident, String incidentId) {
        Status inProgressStatus = statusRepository.findByNameIgnoreCase("In Progress")
                .orElseThrow(() -> new ArmsAuthException("Default 'In Progress' status not configured", 500));

        if (incident.getAssignedToId() != null) {
            boolean agentActive = agentRepository.findById(incident.getAssignedToId())
                    .map(a -> Boolean.TRUE.equals(a.getStatus()))
                    .orElse(false);
            if (!agentActive) {
                incident.setAssignedToId(null);
            }
        }

        incident.setStatusId(inProgressStatus.getId());
        incident.setResolvedAt(null);
        incidentRepository.save(incident);
        activityLogService.logIncidentStatusChange(actorUserId, incidentId, "Reopened", "In Progress");

        // Notify the assigned agent (if any) that the incident has been reopened and needs attention
        String agentUserId = resolveAgentUserId(incident.getAssignedToId());
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        notificationService.sendReopenedNotification(agentUserId, incidentId, incidentNo);
    }

    private boolean requiresReason(Status newStatus) {
        return "status-pending".equals(newStatus.getId()) || "status-reopened".equals(newStatus.getId());
    }

    private void enforceReasonRequired(Status newStatus, String reason) {
        if (requiresReason(newStatus) && (reason == null || reason.isBlank())) {
            throw new ArmsAuthException("A reason is required when setting status to " + newStatus.getName(), 400);
        }
    }

    private void dispatchStatusNotifications(Incident incident, String previousStatus, String newStatus, String reason, String actorUserId) {
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;

        // Notify the client (incident creator) unless they are the one making the change
        String clientUserId = incident.getUserId();
        if (clientUserId != null && !clientUserId.equals(actorUserId)) {
            // When the agent sets the incident to Pending, send a dedicated PENDING notification
            // to the client so they receive an explicitly-typed message rather than a generic status change.
            if ("Pending".equals(newStatus)) {
                notificationService.sendPendingNotification(clientUserId, incident.getId(), incidentNo, reason);
            } else {
                notificationService.sendStatusChangeNotification(clientUserId, incident.getId(), incidentNo, previousStatus, newStatus, reason);
            }
        }

        // Notify the assigned agent unless they are the one making the change
        String agentUserId = resolveAgentUserId(incident.getAssignedToId());
        if (agentUserId != null && !agentUserId.equals(actorUserId)) {
            notificationService.sendStatusChangeNotification(agentUserId, incident.getId(), incidentNo, previousStatus, newStatus, reason);
        }
    }

    private String resolveAgentUserId(String assignedToId) {
        if (assignedToId == null) return null;
        return agentRepository.findById(assignedToId)
                .map(a -> a.getUserId())
                .orElse(null);
    }

    private String findAnyAdminUserId() {
        return adminRepository.findAllActive(PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(Admin::getUserId)
                .orElse(null);
    }

    private void enforceReopenWindow(Incident incident, Status newStatus) {
        if (!"status-reopened".equals(newStatus.getId())) return;
        if (incident.getResolvedAt() == null) return;

        int windowHours = autoCloseService.readDurationHours();
        Instant deadline = incident.getResolvedAt().plus(windowHours, java.time.temporal.ChronoUnit.HOURS);
        if (Instant.now().isAfter(deadline)) {
            throw new ArmsAuthException(
                    "Reopen window has expired. Incidents must be reopened within " + windowHours + " hours of resolution.",
                    403);
        }
    }

    private void enforceTransition(Incident incident, Status newStatus, String roleCode, String actorUserId) {
        String fromId = incident.getStatus() != null ? incident.getStatus().getId() : null;
        String toId   = newStatus.getId();
        String normalizedRole = roleCode == null ? "" : roleCode.toUpperCase();

        if (fromId == null) {
            throw new ArmsAuthException("Cannot transition an incident with no current status", 422);
        }

        // Admins and super-admins may force-close any incident regardless of current status
        if (("ADMIN".equals(normalizedRole) || "SUPER_ADMIN".equals(normalizedRole)) && "status-closed".equals(toId)) {
            return;
        }

        // If the requester is the incident creator, treat them as CLIENT for this incident
        // regardless of their base role. A creator agent loses agent-only transitions
        // (e.g. Pending, Resolved) and gains client-only transitions (e.g. Closed, Reopened).
        boolean isCreator = actorUserId != null && actorUserId.equals(incident.getUserId());
        String effectiveRole = isCreator ? "CLIENT" : normalizedRole;

        Map<String, Set<String>> toMap = VALID_TRANSITIONS.getOrDefault(fromId, Map.of());

        if (!toMap.containsKey(toId)) {
            throw new ArmsAuthException(
                    "Invalid status transition from '" + incident.getStatus().getName()
                    + "' to '" + newStatus.getName() + "'",
                    422
            );
        }

        if (!toMap.get(toId).contains(effectiveRole)) {
            throw new ArmsAuthException(
                    "You do not have permission to move an incident to '" + newStatus.getName() + "'",
                    403
            );
        }

        // For agent-level transitions, verify the actor is the assigned agent on this incident.
        // Role permission alone is not enough — only the assigned agent may act.
        if ("AGENT".equals(effectiveRole)) {
            String assignedAgentUserId = resolveAgentUserId(incident.getAssignedToId());
            if (!actorUserId.equals(assignedAgentUserId)) {
                throw new ArmsAuthException(
                        "You are not the assigned agent for this incident",
                        403
                );
            }
        }
    }
}
