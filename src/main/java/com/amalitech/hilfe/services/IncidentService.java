package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.*;
import com.amalitech.hilfe.notifications.NotificationEventPublisher;
import com.amalitech.hilfe.notifications.events.*;
import com.amalitech.hilfe.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private static final String DEFAULT_PRIORITY_NAME = "Low";
    private static final String STATUS_OPEN = "status-open";
    private static final String STATUS_PENDING = "status-pending";
    private static final String STATUS_RESOLVED = "status-resolved";
    private static final String STATUS_CLOSED = "status-closed";
    private static final String STATUS_REOPENED = "status-reopened";
    private static final String ROLE_AGENT = "AGENT";
    private static final String ROLE_CLIENT = "CLIENT";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String SORT_CREATED_AT = "createdAt";
    private static final String STATUS_NAME_IN_PROGRESS = "In Progress";
    private static final String IN_PROGRESS_NOT_CONFIGURED = "Default 'In Progress' status not configured";

    // Frontend sort alias → JPA field path
    private static final Map<String, String> SORT_FIELD_ALIASES = Map.of(
            "category", "incidentType.category.name",
            "priority", "severity.name"
    );

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            SORT_CREATED_AT, "updatedAt", "title", "incidentNo",
            "incidentType.category.name", "severity.name"
    );

    // from-status-id → to-status-id → roles permitted to make that transition
    private static final Map<String, Map<String, Set<String>>> VALID_TRANSITIONS = Map.of(
            "status-in-progress", Map.of(
                    STATUS_PENDING,  Set.of(ROLE_AGENT),
                    STATUS_RESOLVED, Set.of(ROLE_AGENT)
            ),
            STATUS_PENDING, Map.of(
                    "status-in-progress", Set.of(ROLE_AGENT)
            ),
            STATUS_RESOLVED, Map.of(
                    STATUS_CLOSED,   Set.of(ROLE_CLIENT),
                    STATUS_REOPENED, Set.of(ROLE_CLIENT)
            )
    );

    private final IncidentRepository incidentRepository;
    private final IncidentTypeRepository incidentTypeRepository;
    private final AgentGroupRepository agentGroupRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final AgentRepository agentRepository;
    private final UserRepository userRepository;
    private final StatusRepository statusRepository;
    private final SeverityRepository severityRepository;
    private final AdminRepository adminRepository;
    private final ActivityLogService activityLogService;
    private final NotificationEventPublisher notificationEventPublisher;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;
    private final LocationRepository locationRepository;
    private final AutoCloseService autoCloseService;
    private final SlaService slaService;

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

        String title = request.title() == null ? null : request.title().trim();
        String description = request.description() == null ? null : request.description().trim();

        Incident incident = Incident.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .description(description)
                .userId(userId)
                .locationId(request.locationId())
                .incidentTypeId(request.incidentTypeId())
                .severityId(resolvePriorityId(request.severityId()))
                .build();
        String creatorAgentId = agentRepository.findByUserId(userId).map(Agent::getId).orElse(null);
        applyTopicAssignment(incident, incidentType, creatorAgentId);

        Incident saved = incidentRepository.save(incident);
        entityManager.flush();
        slaService.onIncidentCreated(saved);

        int incidentNo = saved.getIncidentNo() != null ? saved.getIncidentNo() : 0;
        String incidentId = saved.getId();
        String assignedToId = saved.getAssignedToId();
        String agentUserId = resolveAgentUserId(assignedToId);
        List<String> adminUserIds = assignedToId == null ? findAllActiveAdminUserIds() : List.of();

        if (assignedToId != null) {
            String assigneeName = resolveAgentFullName(assignedToId);
            notificationEventPublisher.publish(new IncidentAssignedEvent(agentUserId, incidentId, incidentNo, "System"));
            notificationEventPublisher.publish(new IncidentAutoAssignedClientEvent(userId, incidentId, incidentNo, assigneeName));
            activityLogService.logIncidentAutoAssignment(incidentId, assignedToId);
        } else {
            for (String adminId : adminUserIds) {
                notificationEventPublisher.publish(new IncidentEscalatedEvent(adminId, incidentId, incidentNo));
            }
        }

        List<MediaResponse> mediaResponses = List.of();
        if (request.attachments() != null && !request.attachments().isEmpty()) {
            List<Media> mediaList = mediaService.createMediaForIncident(saved.getId(), request.attachments());
            mediaResponses = mediaService.toMediaResponses(mediaList);
        }

        entityManager.flush();
        entityManager.clear();
        return slaService.toIncidentResponse(
                incidentRepository.findByIdWithDetails(saved.getId()).orElseThrow(),
                mediaResponses);
    }

    public Page<IncidentResponse> queryIncidents(
            String userId,
            String query,
            IncidentFilterParams filters,
            IncidentDateFilter dateFilter,
            Pageable pageable
    ) {
        return slaService.toIncidentResponsePage(incidentRepository
                .findByUserIdUnified(userId, buildQueryPattern(query), filters, dateFilter, ensureSorted(pageable)));
    }

    public Page<IncidentResponse> queryAllIncidents(
            String query,
            IncidentFilterParams filters,
            IncidentDateFilter dateFilter,
            Pageable pageable
    ) {
        return slaService.toIncidentResponsePage(incidentRepository
                .findAllUnified(buildQueryPattern(query), filters, dateFilter, ensureSorted(pageable)));
    }

    public Page<IncidentResponse> queryDeptIncidents(
            String userId,
            String query,
            IncidentFilterParams filters,
            IncidentDateFilter dateFilter,
            Pageable pageable
    ) {
        Pageable sorted = ensureSorted(pageable);
        String queryPattern = buildQueryPattern(query);

        List<String> userGroupIds = findAgentGroupIds(userId).orElse(List.of());
        if (userGroupIds.isEmpty()) {
            return new PageImpl<>(List.of(), sorted, 0);
        }

        List<String> deptIds = agentGroupRepository.findDepartmentIdsByGroupIds(userGroupIds);
        List<String> groupIdsToQuery;
        if (deptIds.isEmpty()) {
            groupIdsToQuery = userGroupIds;
        } else {
            groupIdsToQuery = agentGroupRepository.findIdsByDepartmentIds(deptIds);
        }

        return slaService.toIncidentResponsePage(incidentRepository
                .findByDepartmentUnified(groupIdsToQuery, queryPattern, filters, dateFilter, sorted));
    }

    public Page<IncidentResponse> queryAssignedIncidents(
            String userId,
            String query,
            IncidentFilterParams filters,
            IncidentDateFilter dateFilter,
            Pageable pageable
    ) {
        Pageable sorted = ensureSorted(pageable);
        String queryPattern = buildQueryPattern(query);
        return agentRepository.findByUserId(userId)
                .map(agent -> incidentRepository
                        .findByAssignedToIdUnified(agent.getId(), queryPattern, filters, dateFilter, sorted))
                .map(slaService::toIncidentResponsePage)
                .orElse(new PageImpl<>(List.of(), sorted, 0));
    }

    public Page<IncidentResponse> searchIncidents(String userId, String query, Instant fromDate, Instant toDate, Pageable pageable) {
        if (query == null || query.isBlank()) {
            throw new ArmsAuthException("Search query must not be blank", 400);
        }

        Pageable sortedPageable = pageable.isUnpaged()
                ? Pageable.unpaged(Sort.by(Sort.Direction.DESC, SORT_CREATED_AT))
                : ensureSorted(pageable);

        return slaService.toIncidentResponsePage(incidentRepository
                .searchByUserId(userId, buildQueryPattern(query), fromDate, fromDate != null, toDate, toDate != null, sortedPageable)
        );
    }

    public IncidentResponse getIncident(String userId, String roleCode, String incidentId) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));

        enforceAccess(userId, roleCode, incident);

        List<Media> mediaList = mediaRepository.findByIncidentId(incidentId);
        List<MediaResponse> mediaResponses = mediaService.toMediaResponses(mediaList);
        return slaService.toIncidentResponse(incident, mediaResponses);
    }

    public IncidentResponse getIncident(String userId, RoleCode roleCode, String incidentId) {
        return getIncident(userId, roleCode == null ? null : roleCode.name(), incidentId);
    }

    @Transactional
    public IncidentResponse updateStatus(String actorUserId, String roleCode, String incidentId, UpdateIncidentStatusRequest request) {
        return doUpdateStatus(actorUserId, roleCode, incidentId, request);
    }

    @Transactional
    public IncidentResponse updateStatus(String actorUserId, RoleCode roleCode, String incidentId, UpdateIncidentStatusRequest request) {
        return doUpdateStatus(actorUserId, roleCode == null ? null : roleCode.name(), incidentId, request);
    }

    private IncidentResponse doUpdateStatus(String actorUserId, String roleCode, String incidentId, UpdateIncidentStatusRequest request) {
        Incident incident = findIncident(incidentId);

        Status newStatus = statusRepository.findById(request.statusId())
                .orElseThrow(() -> new ArmsAuthException("Status not found", 404));

        enforceTransition(incident, newStatus, roleCode, actorUserId);
        enforceReopenWindow(incident, newStatus);
        enforceReasonRequired(newStatus, request.reason());

        String previousStatusId = incident.getStatus() != null ? incident.getStatus().getId() : null;
        String previousStatusName = incident.getStatus() != null ? incident.getStatus().getName() : "none";
        incident.setStatusId(request.statusId());
        incident.setStatusReason(requiresReason(newStatus) ? request.reason() : null);
        incident.setResolvedAt(STATUS_RESOLVED.equals(newStatus.getId()) ? Instant.now() : null);
        incident.setClosedAt(STATUS_CLOSED.equals(newStatus.getId()) ? Instant.now() : null);

        incidentRepository.save(incident);
        slaService.onStatusChanged(incident, previousStatusId, request.statusId());
        activityLogService.logIncidentStatusChange(actorUserId, incidentId, previousStatusName, newStatus.getName(), request.reason());
        dispatchStatusNotifications(incident, previousStatusName, newStatus.getName(), request.reason(), actorUserId);

        if ("reopened".equalsIgnoreCase(newStatus.getName())) {
            applyReopenTransition(actorUserId, incident, incidentId);
        }

        entityManager.flush();
        entityManager.clear();
        return slaService.toIncidentResponse(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    @Transactional
    public IncidentResponse updateSeverity(String actorUserId, String incidentId, UpdateIncidentSeverityRequest request) {
        Incident incident = findIncident(incidentId);
        String previousSeverityName = incident.getSeverity() != null ? incident.getSeverity().getName() : "none";
        String newSeverityName = severityRepository.findById(request.severityId())
                .map(s -> s.getName())
                .orElse(request.severityId());
        incident.setSeverityId(request.severityId());
        incidentRepository.save(incident);
        activityLogService.logIncidentSeverityChange(actorUserId, incidentId, previousSeverityName, newSeverityName);

        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;

        // Notify the client (incident creator) that priority was changed, unless they made the change themselves
        String clientUserId = incident.getUserId();
        String actorName = resolveActorName(actorUserId);
        if (clientUserId != null && !clientUserId.equals(actorUserId)) {
            notificationEventPublisher.publish(new IncidentSeverityChangedEvent(
                    clientUserId, incidentId, incidentNo, previousSeverityName, newSeverityName, actorName));
        }

        // Notify the assigned agent that priority was changed, unless they made the change themselves
        String agentUserId = resolveAgentUserId(incident.getAssignedToId());
        if (agentUserId != null && !agentUserId.equals(actorUserId)) {
            notificationEventPublisher.publish(new IncidentSeverityChangedEvent(
                    agentUserId, incidentId, incidentNo, previousSeverityName, newSeverityName, actorName));
        }

        entityManager.flush();
        entityManager.clear();
        return slaService.toIncidentResponse(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    @Transactional
    public IncidentResponse assignIncident(String actorUserId, String roleCode, String incidentId, AssignIncidentRequest request) {
        Incident incident = findIncident(incidentId);

        String normalizedRole = roleCode == null ? "" : roleCode.toUpperCase();
        boolean isAdmin = ROLE_ADMIN.equals(normalizedRole) || ROLE_SUPER_ADMIN.equals(normalizedRole);
        if (!isAdmin) {
            String assignedAgentUserId = resolveAgentUserId(incident.getAssignedToId());
            if (actorUserId == null || !actorUserId.equals(assignedAgentUserId)) {
                throw new ArmsAuthException("You can only reassign incidents that are assigned to you", 403);
            }
        }

        Agent agent = agentRepository.findById(request.agentId())
                .orElseThrow(() -> new ArmsAuthException("Agent not found", 404));
        if (!Boolean.TRUE.equals(agent.getStatus())) {
            throw new ArmsAuthException("Cannot assign incident to an unavailable agent", 400);
        }
        if (agent.getAgentGroupId() != null && !agentRepository.hasActiveGroup(agent.getAgentGroupId(), agent.getId())) {
            throw new ArmsAuthException("Cannot assign incident to an agent in a deactivated group", 400);
        }

        agent.setLastAssignedAt(Instant.now());
        agentRepository.save(agent);

        // Capture the previous agent's userId BEFORE overwriting assignedToId
        String previousAgentUserId = resolveAgentUserId(incident.getAssignedToId());

        incident.setAssignedToId(request.agentId());

        String inProgressStatusId = statusRepository.findByNameIgnoreCase(STATUS_NAME_IN_PROGRESS)
                .orElseThrow(() -> new ArmsAuthException(IN_PROGRESS_NOT_CONFIGURED, 500))
                .getId();
        incident.setStatusId(inProgressStatusId);

        incidentRepository.save(incident);
        activityLogService.logIncidentAssignment(actorUserId, incidentId, request.agentId());

        String agentUserId = resolveAgentUserId(request.agentId());
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        String actorName = resolveActorName(actorUserId);
        String newAssigneeName = resolveAgentFullName(request.agentId());
        notificationEventPublisher.publish(new IncidentAssignedEvent(agentUserId, incidentId, incidentNo, actorName));

        // Notify the previous agent (if any and different from the new agent) that they have been unassigned
        if (previousAgentUserId != null && !previousAgentUserId.equals(agentUserId)) {
            notificationEventPublisher.publish(new IncidentUnassignedEvent(previousAgentUserId, incidentId, incidentNo, actorName, newAssigneeName));
        }

        // Notify the client (incident creator) that a new agent has been assigned
        String clientUserId = incident.getUserId();
        if (clientUserId != null) {
            notificationEventPublisher.publish(new IncidentClientReassignedEvent(clientUserId, incidentId, incidentNo, actorName, newAssigneeName));
        }

        entityManager.flush();
        entityManager.clear();
        return slaService.toIncidentResponse(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
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
        if (pageable.isUnpaged()) return Pageable.unpaged(Sort.by(Sort.Direction.DESC, SORT_CREATED_AT));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, SORT_CREATED_AT));
    }

    private Sort translateSort(Sort sort) {
        List<Sort.Order> orders = sort.stream()
                .map(o -> SORT_FIELD_ALIASES.containsKey(o.getProperty())
                        ? o.withProperty(SORT_FIELD_ALIASES.get(o.getProperty()))
                        : o)
                .filter(o -> ALLOWED_SORT_FIELDS.contains(o.getProperty()))
                .toList();
        if (orders.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, SORT_CREATED_AT);
        }
        return Sort.by(orders);
    }

    private void enforceAccess(String userId, String roleCode, Incident incident) {
        if (ROLE_ADMIN.equalsIgnoreCase(roleCode) || ROLE_SUPER_ADMIN.equalsIgnoreCase(roleCode)) {
            return;
        }
        if (userId.equals(incident.getUserId())) {
            return;
        }
        if (ROLE_AGENT.equalsIgnoreCase(roleCode) && isSameDepartmentAsAssignedAgent(userId, incident)) {
            return;
        }
        throw new ArmsAuthException("You do not have access to this incident", 403);
    }

    private void applyTopicAssignment(Incident incident, IncidentType incidentType, String creatorAgentId) {
        if (incidentType != null
                && incidentType.getAgentGroupId() != null
                && !incidentType.getAgentGroupId().isBlank()) {
            assignViaAgentGroup(incident, incidentType, creatorAgentId);
            return;
        }
        if (incidentType != null && incidentType.getAgentId() != null && !incidentType.getAgentId().isBlank()) {
            assignViaSingleAgent(incident, incidentType, creatorAgentId);
            return;
        }
        incident.setStatusId(STATUS_OPEN);
    }

    private void assignViaAgentGroup(Incident incident, IncidentType incidentType, String creatorAgentId) {
        AgentGroup agentGroup = agentGroupRepository.findById(incidentType.getAgentGroupId())
                .orElseThrow(() -> new ArmsAuthException("Topic agent group not found", 404));

        if (!Boolean.TRUE.equals(agentGroup.getStatus())) {
            incident.setStatusId(STATUS_OPEN);
            return;
        }

        Agent assignedAgent = findAvailableAgentInGroup(agentGroup, incident.getLocationId(), creatorAgentId);
        if (assignedAgent != null) {
            assignedAgent.setLastAssignedAt(Instant.now());
            agentRepository.save(assignedAgent);
            incident.setAssignedToId(assignedAgent.getId());
            incident.setStatusId(statusRepository.findByNameIgnoreCase(STATUS_NAME_IN_PROGRESS)
                    .orElseThrow(() -> new ArmsAuthException(IN_PROGRESS_NOT_CONFIGURED, 500))
                    .getId());
            if (creatorAgentId != null) {
                activityLogService.logSelfAssignmentPrevented(incident.getId(), creatorAgentId, assignedAgent.getId());
            }
        } else {
            incident.setStatusId(STATUS_OPEN);
            if (creatorAgentId != null) {
                activityLogService.logSelfAssignmentEscalated(incident.getId(), creatorAgentId);
            }
        }
    }

    private void assignViaSingleAgent(Incident incident, IncidentType incidentType, String creatorAgentId) {
        boolean isSelf = incidentType.getAgentId().equals(creatorAgentId);
        Agent agent = isSelf ? null : agentRepository.findById(incidentType.getAgentId()).orElse(null);
        if (agent != null && Boolean.TRUE.equals(agent.getStatus())) {
            agent.setLastAssignedAt(Instant.now());
            agentRepository.save(agent);
            incident.setAssignedToId(incidentType.getAgentId());
            incident.setStatusId(statusRepository.findByNameIgnoreCase(STATUS_NAME_IN_PROGRESS)
                    .orElseThrow(() -> new ArmsAuthException(IN_PROGRESS_NOT_CONFIGURED, 500))
                    .getId());
        } else {
            incident.setStatusId(STATUS_OPEN);
            if (isSelf) {
                activityLogService.logSelfAssignmentEscalated(incident.getId(), creatorAgentId);
            }
        }
    }

    private Agent findAvailableAgentInGroup(AgentGroup agentGroup, String locationId, String excludeAgentId) {
        List<Agent> locationMatched = agentRepository
                .findAvailableByAgentGroupIdAndLocation(agentGroup.getId(), locationId);
        Agent fromLocation = locationMatched.stream()
                .filter(a -> !a.getId().equals(excludeAgentId))
                .findFirst().orElse(null);
        if (fromLocation != null) {
            return fromLocation;
        }
        List<Agent> anyAvailable = agentRepository
                .findAvailableByAgentGroupIdViaMembership(agentGroup.getId());
        return anyAvailable.stream()
                .filter(a -> !a.getId().equals(excludeAgentId))
                .findFirst().orElse(null);
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
        Status inProgressStatus = statusRepository.findByNameIgnoreCase(STATUS_NAME_IN_PROGRESS)
                .orElseThrow(() -> new ArmsAuthException(IN_PROGRESS_NOT_CONFIGURED, 500));

        String previousAgentId = incident.getAssignedToId();
        if (previousAgentId != null) {
            boolean agentActive = agentRepository.findById(previousAgentId)
                    .map(a -> Boolean.TRUE.equals(a.getStatus()))
                    .orElse(false);
            if (!agentActive) {
                incident.setAssignedToId(null);
            }
        }

        incident.setStatusId(inProgressStatus.getId());
        incident.setResolvedAt(null);
        incidentRepository.save(incident);
        activityLogService.logIncidentStatusChange(actorUserId, incidentId, "Reopened", STATUS_NAME_IN_PROGRESS);
        if (incident.getAssignedToId() == null && previousAgentId != null) {
            activityLogService.logIncidentUnassignment(actorUserId, incidentId, previousAgentId);
        }

        // Notify the assigned agent (if any) that the incident has been reopened and needs attention
        String agentUserId = resolveAgentUserId(incident.getAssignedToId());
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        notificationEventPublisher.publish(new IncidentReopenedEvent(agentUserId, incidentId, incidentNo, resolveActorName(actorUserId)));
    }

    private boolean requiresReason(Status newStatus) {
        return STATUS_PENDING.equals(newStatus.getId()) || STATUS_REOPENED.equals(newStatus.getId());
    }

    private void enforceReasonRequired(Status newStatus, String reason) {
        if (requiresReason(newStatus) && (reason == null || reason.isBlank())) {
            throw new ArmsAuthException("A reason is required when setting status to " + newStatus.getName(), 400);
        }
    }

    private void dispatchStatusNotifications(Incident incident, String previousStatus, String newStatus, String reason, String actorUserId) {
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        String actorName = resolveActorName(actorUserId);

        // Notify the client (incident creator) unless they are the one making the change
        String clientUserId = incident.getUserId();
        if (clientUserId != null && !clientUserId.equals(actorUserId)) {
            // When the agent sets the incident to Pending, send a dedicated PENDING notification
            // to the client so they receive an explicitly-typed message rather than a generic status change.
            if ("Pending".equals(newStatus)) {
                notificationEventPublisher.publish(new IncidentPendingEvent(clientUserId, incident.getId(), incidentNo, reason, actorName));
            } else {
                notificationEventPublisher.publish(new IncidentStatusChangedEvent(
                        clientUserId, incident.getId(), incidentNo, previousStatus, newStatus, reason, actorName));
            }
        }

        // Notify the assigned agent unless they are the one making the change
        String agentUserId = resolveAgentUserId(incident.getAssignedToId());
        if (agentUserId != null && !agentUserId.equals(actorUserId)) {
            notificationEventPublisher.publish(new IncidentStatusChangedEvent(
                    agentUserId, incident.getId(), incidentNo, previousStatus, newStatus, reason, actorName));
        }
    }

    private String resolveAgentUserId(String assignedToId) {
        if (assignedToId == null) return null;
        return agentRepository.findById(assignedToId)
                .map(Agent::getUserId)
                .orElse(null);
    }

    private String resolveActorName(String userId) {
        if (userId == null) return "System";
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("Deactivated User");
    }

    private String resolveAgentFullName(String agentId) {
        if (agentId == null) return "Unknown Agent";
        return resolveActorName(resolveAgentUserId(agentId));
    }

    private List<String> findAllActiveAdminUserIds() {
        // Query User table directly — does not depend on Admin table being populated,
        // so admins who were promoted before ensureAdminRecord was added are included.
        return userRepository.findActiveAdminUserIds();
    }

    private void enforceReopenWindow(Incident incident, Status newStatus) {
        if (!STATUS_REOPENED.equals(newStatus.getId())) return;
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
        if ((ROLE_ADMIN.equals(normalizedRole) || ROLE_SUPER_ADMIN.equals(normalizedRole)) && STATUS_CLOSED.equals(toId)) {
            return;
        }

        // If the requester is the incident creator, treat them as CLIENT for this incident
        // regardless of their base role. A creator agent loses agent-only transitions
        // (e.g. Pending, Resolved) and gains client-only transitions (e.g. Closed, Reopened).
        boolean isCreator = actorUserId != null && actorUserId.equals(incident.getUserId());
        String effectiveRole = isCreator ? ROLE_CLIENT : normalizedRole;

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
        if (ROLE_AGENT.equals(effectiveRole)) {
            String assignedAgentUserId = resolveAgentUserId(incident.getAssignedToId());
            if (actorUserId == null || !actorUserId.equals(assignedAgentUserId)) {
                throw new ArmsAuthException(
                        "You are not the assigned agent for this incident",
                        403
                );
            }
        }
    }
}
