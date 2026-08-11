package com.amalitech.hilfe.services;

import com.amalitech.hilfe.constants.ApiMessages;
import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.*;
import com.amalitech.hilfe.notifications.NotificationEventPublisher;
import com.amalitech.hilfe.notifications.events.*;
import com.amalitech.hilfe.repositories.*;
import com.amalitech.hilfe.security.authorization.CurrentUserAuthority;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
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
    private static final String STATUS_UNASSIGNED = "status-unassigned";
    private static final String STATUS_PENDING = "status-pending";
    private static final String STATUS_RESOLVED = "status-resolved";
    private static final String STATUS_CLOSED = "status-closed";
    private static final String STATUS_REOPENED = "status-reopened";
    private static final String ROLE_AGENT = "AGENT";
    private static final String ROLE_CLIENT = "CLIENT";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_ADMIN_AGENT = "ADMIN_AGENT";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String SORT_CREATED_AT = "createdAt";
    private static final String SORT_UPDATED_AT = "updatedAt";
    private static final String SORT_TITLE = "title";
    private static final String SORT_INCIDENT_NO = "incidentNo";
    private static final String SORT_CATEGORY_NAME = "incidentType.category.name";
    private static final String SORT_SEVERITY_NAME = "severity.name";
    private static final String SORT_STATUS_NAME = "status.name";
    private static final String STATUS_NAME_IN_PROGRESS = "In Progress";
    private static final String IN_PROGRESS_NOT_CONFIGURED = "Default 'In Progress' status not configured";

    // Frontend sort alias → JPA field path
    private static final Map<String, String> SORT_FIELD_ALIASES = Map.of(
            "category", SORT_CATEGORY_NAME,
            "priority", SORT_SEVERITY_NAME,
            "priority.name", SORT_SEVERITY_NAME,
            "status", SORT_STATUS_NAME
    );

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            SORT_CREATED_AT, SORT_UPDATED_AT, SORT_TITLE, SORT_INCIDENT_NO,
            SORT_CATEGORY_NAME, SORT_SEVERITY_NAME, SORT_STATUS_NAME
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
    private final IncidentCategoryRepository incidentCategoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public IncidentResponse createIncident(String userId, CreateIncidentRequest request) {
        List<String> notFound = new java.util.ArrayList<>();
        IncidentType incidentType = incidentTypeRepository.findById(request.incidentTypeId()).orElse(null);
        if (incidentType == null) {
            notFound.add("Incident type with the provided ID could not be found.");
        } else if (!Boolean.TRUE.equals(incidentType.getStatus())) {
            notFound.add("Incident type with the provided ID is not currently active.");
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
        String creatorAgentId = agentRepository.findByUserId(userId)
                .filter(a -> Boolean.TRUE.equals(a.getStatus()))
                .map(Agent::getId).orElse(null);
        applyTopicAssignment(incident, incidentType, creatorAgentId);

        Incident saved = incidentRepository.save(incident);
        entityManager.flush();
        slaService.onIncidentCreated(saved);

        int incidentNo = saved.getIncidentNo() != null ? saved.getIncidentNo() : 0;
        String incidentId = saved.getId();
        String assignedToId = saved.getAssignedToId();
        String agentUserId = resolveAgentUserId(assignedToId);
        List<String> adminUserIds = assignedToId == null ? resolveEscalationRecipientUserIds(incidentType) : List.of();

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
            List<Media> mediaList = mediaService.createMediaForIncident(saved.getId(), request.attachments(), userId);
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
        requireAdminForSlaStatusFilter(filters);
        return slaService.toIncidentResponsePage(incidentRepository
                .findByUserIdUnified(userId, buildQueryPattern(query), filters, dateFilter, ensureSorted(pageable)));
    }

    // The SLA status filter is admin-only per HV-1477. queryAllIncidents' endpoint is already
    // @PreAuthorize'd to dashboard.admin only, so it doesn't need this check — but myIncidents has no
    // @PreAuthorize at all, and deptIncidents/assignedIncidents allow dashboard.agent too, so both
    // need this service-layer guard to keep agents and clients from using the filter.
    private void requireAdminForSlaStatusFilter(IncidentFilterParams filters) {
        if (filters != null && filters.slaStatus() != null && !CurrentUserAuthority.has(RbacPermissions.DASHBOARD_ADMIN)) {
            throw new ArmsAuthException("The SLA status filter is only available to admin users.", 403);
        }
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
        requireAdminForSlaStatusFilter(filters);
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
        requireAdminForSlaStatusFilter(filters);
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
            throw new ArmsAuthException("Please enter a search term.", 400);
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
    public IncidentResponse updateReadStatus(String actorUserId, String roleCode, String incidentId, boolean read) {
        return doUpdateReadStatus(actorUserId, roleCode, incidentId, read);
    }

    @Transactional
    public IncidentResponse updateReadStatus(String actorUserId, RoleCode roleCode, String incidentId, boolean read) {
        return doUpdateReadStatus(actorUserId, roleCode == null ? null : roleCode.name(), incidentId, read);
    }

    // The frontend prefetches rows to speed up incident-detail navigation, which would fire
    // getIncident() speculatively — so marking read/logging a view can't live there. This is the
    // one explicit trigger for both, called only when the user actually opens the incident.
    private IncidentResponse doUpdateReadStatus(String actorUserId, String roleCode, String incidentId, boolean read) {
        Incident incident = findIncident(incidentId);
        enforceAccess(actorUserId, roleCode, incident);
        incident.setRead(read);
        incidentRepository.save(incident);
        if (read) {
            activityLogService.logIncidentViewed(actorUserId, incidentId);
        }
        entityManager.flush();
        entityManager.clear();
        return slaService.toIncidentResponse(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    @Transactional
    public IncidentResponse updateStatus(String actorUserId, String roleCode, boolean hasForceClose, String incidentId, UpdateIncidentStatusRequest request) {
        return doUpdateStatus(actorUserId, roleCode, hasForceClose, incidentId, request);
    }

    @Transactional
    public IncidentResponse updateStatus(String actorUserId, RoleCode roleCode, boolean hasForceClose, String incidentId, UpdateIncidentStatusRequest request) {
        return doUpdateStatus(actorUserId, roleCode == null ? null : roleCode.name(), hasForceClose, incidentId, request);
    }

    private IncidentResponse doUpdateStatus(String actorUserId, String roleCode, boolean hasForceClose, String incidentId, UpdateIncidentStatusRequest request) {
        Incident incident = findIncident(incidentId);

        Status newStatus = statusRepository.findById(request.statusId())
                .orElseThrow(() -> new ArmsAuthException("Status not found", 404));

        enforceTransition(incident, newStatus, roleCode, actorUserId, hasForceClose);
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
    public IncidentResponse updateSeverity(String actorUserId, boolean hasUpdateAny, String incidentId, UpdateIncidentSeverityRequest request) {
        Incident incident = findIncident(incidentId);
        enforceUpdateOwnership(actorUserId, hasUpdateAny, incident,
                "You can only update the severity of incidents assigned to you");
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
    public IncidentResponse assignIncident(String actorUserId, boolean hasUpdateAny, String incidentId, AssignIncidentRequest request) {
        Incident incident = findIncident(incidentId);

        enforceUpdateOwnership(actorUserId, hasUpdateAny, incident,
                "You do not have permission to reassign this incident.");

        if (STATUS_RESOLVED.equals(incident.getStatusId())) {
            throw new ArmsAuthException("You cannot reassign a Resolved incident.", 400);
        }

        Agent agent = agentRepository.findById(request.agentId())
                .orElseThrow(() -> new ArmsAuthException("Agent not found", 404));
        if (!Boolean.TRUE.equals(agent.getStatus())) {
            throw new ArmsAuthException("This incident cannot be assigned to an unavailable agent.", 400);
        }
        if (agent.getAgentGroupId() != null && !agentRepository.hasActiveGroup(agent.getAgentGroupId(), agent.getId())) {
            throw new ArmsAuthException("This incident cannot be assigned to an agent in a deactivated group.", 400);
        }

        agent.setLastAssignedAt(Instant.now());
        agentRepository.save(agent);

        // Capture the previous agent's userId BEFORE overwriting assignedToId
        String previousAgentUserId = resolveAgentUserId(incident.getAssignedToId());
        boolean isFirstAssignment = incident.getAssignedToId() == null;
        String agentUserId = resolveAgentUserId(request.agentId());

        // A reassignment is a previous agent being replaced by a different agent; anything else
        // (no previous agent, or reassigning to the same agent) is treated as an initial assignment.
        boolean isReassignment = previousAgentUserId != null && !previousAgentUserId.equals(agentUserId);

        incident.setAssignedToId(request.agentId());

        // A first pickup and a reassignment both hand the incident to someone who hasn't replied
        // yet, so both land on Open -- never re-activate straight to In Progress here; that only
        // happens once the newly assigned agent actually sends their first chat reply (see
        // onFirstAgentResponse). Never clobber a status set in the same edit otherwise.
        if (isFirstAssignment || isReassignment) {
            incident.setStatusId(STATUS_OPEN);
        }
        if (isReassignment) {
            // The new agent hasn't responded yet -- restart the response timer so their first
            // reply still triggers Open->In Progress instead of being silently swallowed by the
            // "already responded" guard left over from the previous agent.
            resetFirstResponseTracking(incident);
        }

        incidentRepository.save(incident);
        activityLogService.logIncidentAssignment(actorUserId, incidentId, request.agentId());

        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        String actorName = resolveActorName(actorUserId);
        String newAssigneeName = resolveAgentFullName(request.agentId());

        if (isReassignment) {
            notificationEventPublisher.publish(new IncidentReassignedEvent(agentUserId, incidentId, incidentNo, actorName));
            notificationEventPublisher.publish(new IncidentUnassignedEvent(previousAgentUserId, incidentId, incidentNo, actorName, newAssigneeName));
        } else {
            notificationEventPublisher.publish(new IncidentAssignedEvent(agentUserId, incidentId, incidentNo, actorName));
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
        if (ROLE_ADMIN.equalsIgnoreCase(roleCode) || ROLE_ADMIN_AGENT.equalsIgnoreCase(roleCode) || ROLE_SUPER_ADMIN.equalsIgnoreCase(roleCode)) {
            return;
        }
        if (userId.equals(incident.getUserId())) {
            return;
        }
        if (ROLE_AGENT.equalsIgnoreCase(roleCode)) {
            boolean isAssignee = agentRepository.findByUserId(userId)
                    .map(a -> a.getId().equals(incident.getAssignedToId()))
                    .orElse(false);
            if (isAssignee) return;
            if (isSameDepartmentAsAssignedAgent(userId, incident)) return;
        }
        throw new ArmsAuthException("You do not have permission to access this incident.", 403);
    }

    /**
     * Cross-incident update access (reassigning, changing severity of an incident the actor
     * neither owns nor is assigned to) requires the incident.update.any permission. Everyone
     * else is restricted to incidents assigned to them.
     */
    private void enforceUpdateOwnership(String actorUserId, boolean hasUpdateAny, Incident incident, String deniedMessage) {
        if (hasUpdateAny) {
            return;
        }
        String assignedAgentUserId = resolveAgentUserId(incident.getAssignedToId());
        if (actorUserId == null || !actorUserId.equals(assignedAgentUserId)) {
            throw new ArmsAuthException(deniedMessage, 403);
        }
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
        // No topic owner configured at all -- nobody to route this to.
        incident.setStatusId(STATUS_UNASSIGNED);
    }

    private void assignViaAgentGroup(Incident incident, IncidentType incidentType, String creatorAgentId) {
        AgentGroup agentGroup = agentGroupRepository.findById(incidentType.getAgentGroupId())
                .orElseThrow(() -> new ArmsAuthException("Topic agent group not found", 404));

        if (!Boolean.TRUE.equals(agentGroup.getStatus())) {
            incident.setStatusId(STATUS_UNASSIGNED);
            return;
        }

        Agent assignedAgent = findAvailableAgentInGroup(agentGroup, incident.getLocationId(), creatorAgentId);
        if (assignedAgent != null) {
            assignedAgent.setLastAssignedAt(Instant.now());
            agentRepository.save(assignedAgent);
            incident.setAssignedToId(assignedAgent.getId());
            // Auto-assigned but not yet responded to -- Open, not In Progress. The transition to
            // In Progress happens only once this agent sends their first chat reply (see
            // onFirstAgentResponse).
            incident.setStatusId(STATUS_OPEN);
            if (creatorAgentId != null) {
                activityLogService.logSelfAssignmentPrevented(incident.getId(), creatorAgentId, assignedAgent.getId());
            }
        } else {
            // No agent available to auto-assign -- nobody is on this yet.
            incident.setStatusId(STATUS_UNASSIGNED);
            if (creatorAgentId != null) {
                activityLogService.logSelfAssignmentEscalated(incident.getId(), creatorAgentId);
            }
        }
    }

    private void assignViaSingleAgent(Incident incident, IncidentType incidentType, String creatorAgentId) {
        boolean isSelf = incidentType.getAgentId().equals(creatorAgentId);
        Agent agent = isSelf ? null : agentRepository.findByIdWithUser(incidentType.getAgentId()).orElse(null);
        boolean available = agent != null
                && Boolean.TRUE.equals(agent.getStatus())
                && Boolean.TRUE.equals(agent.getUser().getStatus());
        if (available) {
            agent.setLastAssignedAt(Instant.now());
            agentRepository.save(agent);
            incident.setAssignedToId(incidentType.getAgentId());
            // Auto-assigned but not yet responded to -- Open, not In Progress (see above).
            incident.setStatusId(STATUS_OPEN);
        } else {
            // No agent available to auto-assign -- nobody is on this yet.
            incident.setStatusId(STATUS_UNASSIGNED);
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
        List<String> actorGroupIds = findAgentGroupIds(userId).orElse(List.of());
        if (actorGroupIds.isEmpty()) {
            return false;
        }
        List<String> assignedGroupIds = agentGroupMemberRepository.findAgentGroupIdsByAgentId(incident.getAssignedToId());
        if (assignedGroupIds.isEmpty()) {
            return false;
        }

        // Compare by department rather than by group so that agents in different
        // groups within the same department can access each other's incidents,
        // consistent with the department-incidents listing query.
        List<String> actorDeptIds = agentGroupRepository.findDepartmentIdsByGroupIds(actorGroupIds);
        if (actorDeptIds.isEmpty()) {
            // No department hierarchy — fall back to direct group overlap
            return assignedGroupIds.stream().anyMatch(actorGroupIds::contains);
        }
        List<String> assignedDeptIds = agentGroupRepository.findDepartmentIdsByGroupIds(assignedGroupIds);
        return assignedDeptIds.stream().anyMatch(actorDeptIds::contains);
    }

    private Incident findIncident(String incidentId) {
        return incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
    }

    private String resolvePriorityId(String requestedSeverityId) {
        if (requestedSeverityId != null && !requestedSeverityId.isBlank()) {
            if (!severityRepository.existsById(requestedSeverityId)) {
                throw new ArmsAuthException(ApiMessages.SEVERITY_NOT_FOUND, 404);
            }
            return requestedSeverityId;
        }

        Severity defaultPriority = severityRepository.findByNameIgnoreCase(DEFAULT_PRIORITY_NAME)
                .orElseThrow(() -> new ArmsAuthException("Default 'Low' priority not configured", 500));
        return defaultPriority.getId();
    }

    private void applyReopenTransition(String actorUserId, Incident incident, String incidentId) {
        String previousAgentId = incident.getAssignedToId();
        boolean agentActive = previousAgentId != null && agentRepository.findById(previousAgentId)
                .map(a -> Boolean.TRUE.equals(a.getStatus()))
                .orElse(false);

        String newStatusName;
        if (previousAgentId != null && !agentActive) {
            // No live agent to hand this back to -- Unassigned, not a phantom "In Progress" with
            // nobody actually assigned to it.
            resetToUnassigned(incident);
            newStatusName = "Unassigned";
        } else {
            Status inProgressStatus = statusRepository.findByNameIgnoreCase(STATUS_NAME_IN_PROGRESS)
                    .orElseThrow(() -> new ArmsAuthException(IN_PROGRESS_NOT_CONFIGURED, 500));
            incident.setStatusId(inProgressStatus.getId());
            newStatusName = STATUS_NAME_IN_PROGRESS;
        }

        incident.setResolvedAt(null);
        incidentRepository.save(incident);
        activityLogService.logIncidentStatusChange(actorUserId, incidentId, "Reopened", newStatusName);
        if (incident.getAssignedToId() == null && previousAgentId != null) {
            activityLogService.logIncidentUnassignment(actorUserId, incidentId, previousAgentId);
        }

        // Notify the assigned agent (if any) that the incident has been reopened and needs attention
        String agentUserId = resolveAgentUserId(incident.getAssignedToId());
        int incidentNo = incident.getIncidentNo() != null ? incident.getIncidentNo() : 0;
        notificationEventPublisher.publish(new IncidentReopenedEvent(agentUserId, incidentId, incidentNo, resolveActorName(actorUserId)));
    }

    /**
     * Puts an incident back to square one: no assignee, Unassigned status, and a clean response
     * timer for whoever picks it up next. Shared by the reopen flow above (previous agent has
     * gone inactive) and agent-deactivation handling (unassignAllForDeactivatedAgent) so both
     * "this incident has no one who can respond to it right now" cases behave identically.
     * Callers are responsible for persisting the incident afterward.
     */
    private void resetToUnassigned(Incident incident) {
        incident.setAssignedToId(null);
        incident.setStatusId(STATUS_UNASSIGNED);
        slaService.resetFirstResponse(incident.getId());
    }

    /**
     * Restarts response-SLA tracking without touching assignment/status -- used when the caller
     * is already setting those fields itself (e.g. assignIncident's reassignment branch).
     */
    private void resetFirstResponseTracking(Incident incident) {
        slaService.resetFirstResponse(incident.getId());
    }

    /**
     * Moves an incident from Open to In Progress the moment its assigned agent sends their first
     * chat reply. A no-op for any other current status -- this only ever fires once per incident
     * per assignment, driven by SlaService#onAgentMessageSent reporting a genuine first response.
     */
    @Transactional
    public void onFirstAgentResponse(Incident incident, String actorUserId) {
        if (!STATUS_OPEN.equals(incident.getStatusId())) {
            return;
        }
        String inProgressId = statusRepository.findByNameIgnoreCase(STATUS_NAME_IN_PROGRESS)
                .orElseThrow(() -> new ArmsAuthException(IN_PROGRESS_NOT_CONFIGURED, 500))
                .getId();
        incident.setStatusId(inProgressId);
        incidentRepository.save(incident);
        activityLogService.logIncidentStatusChange(actorUserId, incident.getId(), "Open", STATUS_NAME_IN_PROGRESS);
        dispatchStatusNotifications(incident, "Open", STATUS_NAME_IN_PROGRESS, null, actorUserId);
    }

    /**
     * Called whenever an agent account is deactivated (UserService#updateUserStatus,
     * AdminService, RoleAccessSyncService) -- any incident still assigned to them that hasn't
     * reached a terminal status goes back to Unassigned rather than silently staying "assigned"
     * to an agent who can no longer act on it. actorUserId may be null for system-driven
     * deactivations that have no human actor to attribute the resulting unassignment to.
     */
    @Transactional
    public void unassignAllForDeactivatedAgent(String agentId, String actorUserId) {
        List<Incident> openIncidents = incidentRepository
                .findByAssignedToIdAndStatusIdNotIn(agentId, List.of(STATUS_RESOLVED, STATUS_CLOSED));
        for (Incident incident : openIncidents) {
            resetToUnassigned(incident);
            incidentRepository.save(incident);
            activityLogService.logIncidentUnassignment(actorUserId, incident.getId(), agentId);
        }
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

    // HV-1533: escalate to the incident's department head instead of broadcasting to all admins.
    // Falls back to all admins when there's no category/department link, no head is set, or the
    // stored head is no longer an active admin-capable user (role/status can change after assignment).
    private List<String> resolveEscalationRecipientUserIds(IncidentType incidentType) {
        String categoryId = incidentType != null ? incidentType.getCategoryId() : null;
        if (categoryId != null) {
            String headUserId = incidentCategoryRepository.findByIdWithDepartment(categoryId)
                    .map(IncidentCategory::getDepartment)
                    .map(Department::getHeadUserId)
                    .orElse(null);
            if (headUserId != null && isActiveAdminCapableUser(headUserId)) {
                return List.of(headUserId);
            }
        }
        return findAllActiveAdminUserIds();
    }

    private boolean isActiveAdminCapableUser(String userId) {
        return userRepository.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getStatus()))
                .map(u -> {
                    RoleCode role = RoleCode.valueOf(u.getRoleCode().toUpperCase());
                    return role == RoleCode.ADMIN || role == RoleCode.ADMIN_AGENT || role == RoleCode.SUPER_ADMIN;
                })
                .orElse(false);
    }

    private void enforceReopenWindow(Incident incident, Status newStatus) {
        if (!STATUS_REOPENED.equals(newStatus.getId())) return;
        if (incident.getResolvedAt() == null) return;

        int windowSeconds = autoCloseService.readDurationSeconds();
        Instant deadline = incident.getResolvedAt().plusSeconds(windowSeconds);
        if (Instant.now().isAfter(deadline)) {
            throw new ArmsAuthException(
                    "Reopen window has expired. Incidents must be reopened within " + windowSeconds + " seconds of resolution.",
                    403);
        }
    }

    private void enforceTransition(Incident incident, Status newStatus, String roleCode, String actorUserId, boolean hasForceClose) {
        String fromId = incident.getStatus() != null ? incident.getStatus().getId() : null;
        String toId   = newStatus.getId();
        String normalizedRole = roleCode == null ? "" : roleCode.toUpperCase();

        if (fromId == null) {
            throw new ArmsAuthException("This incident does not have a current status to transition from.", 422);
        }

        // Holders of incident.forceclose may force-close any incident regardless of current status
        if (hasForceClose && STATUS_CLOSED.equals(toId)) {
            return;
        }

        // Open has no VALID_TRANSITIONS entry by design -- the only way out of Open is the
        // assigned agent's first chat reply (see onFirstAgentResponse), never a direct status
        // update. Give a friendly, specific message instead of falling through to the generic
        // "cannot be moved from X to Y" below.
        if (STATUS_OPEN.equals(fromId)) {
            throw new ArmsAuthException(
                    "This incident is Open. Send a message in the chat to move it to 'In Progress' before changing its status.",
                    422
            );
        }

        // If the requester is the incident creator, treat them as CLIENT for this incident
        // regardless of their base role. A creator agent loses agent-only transitions
        // (e.g. Pending, Resolved) and gains client-only transitions (e.g. Closed, Reopened).
        boolean isCreator = actorUserId != null && actorUserId.equals(incident.getUserId());
        // ADMIN_AGENT performs agent-level status transitions the same way a standard agent does
        String normalizedForTransition = ROLE_ADMIN_AGENT.equals(normalizedRole) ? ROLE_AGENT : normalizedRole;
        String effectiveRole = isCreator ? ROLE_CLIENT : normalizedForTransition;

        Map<String, Set<String>> toMap = VALID_TRANSITIONS.getOrDefault(fromId, Map.of());

        if (!toMap.containsKey(toId)) {
            throw new ArmsAuthException(
                    "This incident cannot be moved from '" + incident.getStatus().getName()
                    + "' to '" + newStatus.getName() + "'.",
                    422
            );
        }

        if (!toMap.get(toId).contains(effectiveRole)) {
            throw new ArmsAuthException(
                    "You do not have permission to move this incident to '" + newStatus.getName() + "'.",
                    403
            );
        }

        // For agent-level transitions, verify the actor is the assigned agent on this incident.
        // Role permission alone is not enough — only the assigned agent may act.
        if (ROLE_AGENT.equals(effectiveRole)) {
            String assignedAgentUserId = resolveAgentUserId(incident.getAssignedToId());
            if (actorUserId == null || !actorUserId.equals(assignedAgentUserId)) {
                throw new ArmsAuthException(
                        "You do not have permission to update this incident because you are not the assigned agent.",
                        403
                );
            }
        }
    }
}
