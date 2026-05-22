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

    // from-status-id → to-status-id → roles permitted to make that transition
    private static final Map<String, Map<String, Set<RoleCode>>> VALID_TRANSITIONS = Map.of(
            "status-in-progress", Map.of(
                    "status-pending",  Set.of(RoleCode.AGENT),
                    "status-resolved", Set.of(RoleCode.AGENT)
            ),
            "status-pending", Map.of(
                    "status-in-progress", Set.of(RoleCode.AGENT)
            ),
            "status-resolved", Map.of(
                    "status-closed",   Set.of(RoleCode.CLIENT),
                    "status-reopened", Set.of(RoleCode.CLIENT)
            )
    );

    private final IncidentRepository incidentRepository;
    private final IncidentTypeRepository incidentTypeRepository;
    private final AgentGroupRepository agentGroupRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final AgentRepository agentRepository;
    private final StatusRepository statusRepository;
    private final SeverityRepository severityRepository;
    private final ActivityLogService activityLogService;
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
            Pageable pageable
    ) {
        String queryPattern = null;
        if (query != null && !query.isBlank()) {
            String escaped = query.toLowerCase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            queryPattern = "%" + escaped + "%";
        }

        final String finalQueryPattern = queryPattern;
        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));

        return incidentRepository
                .findByUserIdUnified(userId, finalQueryPattern, statusId, severityId, incidentTypeId, categoryId, locationId, sortedPageable)
                .map(IncidentResponse::from);
    }

    public Page<IncidentResponse> searchIncidents(String userId, String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            throw new ArmsAuthException("Search query must not be blank", 400);
        }

        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : pageable.isUnpaged()
                        ? Pageable.unpaged(Sort.by(Sort.Direction.DESC, "createdAt"))
                        : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                Sort.by(Sort.Direction.DESC, "createdAt"));

        String escaped = query.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String queryPattern = "%" + escaped + "%";

        return incidentRepository
                .searchByUserId(userId, queryPattern, sortedPageable)
                .map(IncidentResponse::from);
    }

    public IncidentResponse getIncident(String userId, RoleCode roleCode, String incidentId) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));

        enforceAccess(userId, roleCode, incident);

        List<Media> mediaList = mediaRepository.findByIncidentId(incidentId);
        List<MediaResponse> mediaResponses = mediaService.toMediaResponses(mediaList);
        return IncidentResponse.from(incident, mediaResponses);
    }

    @Transactional
    public IncidentResponse updateStatus(String actorUserId, RoleCode roleCode, String incidentId, UpdateIncidentStatusRequest request) {
        Incident incident = findIncident(incidentId);

        Status newStatus = statusRepository.findById(request.statusId())
                .orElseThrow(() -> new ArmsAuthException("Status not found", 404));

        enforceTransition(incident, newStatus, roleCode);
        enforceReopenWindow(incident, newStatus);

        String previousStatusName = incident.getStatus() != null ? incident.getStatus().getName() : "none";
        incident.setStatusId(request.statusId());
        incident.setResolvedAt("status-resolved".equals(newStatus.getId()) ? Instant.now() : null);
        incident.setClosedAt("status-closed".equals(newStatus.getId()) ? Instant.now() : null);

        incidentRepository.save(incident);
        activityLogService.logIncidentStatusChange(actorUserId, incidentId, previousStatusName, newStatus.getName());

        if ("reopened".equalsIgnoreCase(newStatus.getName())) {
            applyReopenTransition(actorUserId, incident, incidentId);
        }

        entityManager.flush();
        entityManager.clear();
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
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
        incident.setAssignedToId(request.agentId());

        String inProgressStatusId = statusRepository.findByNameIgnoreCase("In Progress")
                .orElseThrow(() -> new ArmsAuthException("Default 'In Progress' status not configured", 500))
                .getId();
        incident.setStatusId(inProgressStatusId);

        incidentRepository.save(incident);
        activityLogService.logIncidentAssignment(actorUserId, incidentId, request.agentId());
        entityManager.flush();
        entityManager.clear();
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void enforceAccess(String userId, RoleCode roleCode, Incident incident) {
        if (roleCode == RoleCode.ADMIN || roleCode == RoleCode.SUPER_ADMIN) {
            return;
        }
        if (userId.equals(incident.getUserId())) {
            return;
        }
        if (roleCode == RoleCode.AGENT && isSameDepartmentAsAssignedAgent(userId, incident)) {
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
            if (agentGroup.getPrimaryAgentId() == null || agentGroup.getPrimaryAgentId().isBlank()) {
                throw new ArmsAuthException("Topic agent group has no primary agent", 400);
            }
            incident.setAssignedToId(agentGroup.getPrimaryAgentId());
            incident.setStatusId(statusRepository.findByNameIgnoreCase("Pending")
                    .orElseThrow(() -> new ArmsAuthException("Default 'Pending' status not configured", 500))
                    .getId());
            return;
        }
        if (incidentType != null && incidentType.getAgentId() != null && !incidentType.getAgentId().isBlank()) {
            incident.setAssignedToId(incidentType.getAgentId());
            incident.setStatusId(statusRepository.findByNameIgnoreCase("Pending")
                    .orElseThrow(() -> new ArmsAuthException("Default 'Pending' status not configured", 500))
                    .getId());
            return;
        }
        incident.setStatusId("status-open");
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

    private void enforceTransition(Incident incident, Status newStatus, RoleCode roleCode) {
        String fromId = incident.getStatus() != null ? incident.getStatus().getId() : null;
        String toId   = newStatus.getId();

        if (fromId == null) {
            throw new ArmsAuthException("Cannot transition an incident with no current status", 422);
        }

        // Admins and super-admins may force-close any incident regardless of current status
        if ((roleCode == RoleCode.ADMIN || roleCode == RoleCode.SUPER_ADMIN) && "status-closed".equals(toId)) {
            return;
        }

        Map<String, Set<RoleCode>> toMap = VALID_TRANSITIONS.getOrDefault(fromId, Map.of());

        if (!toMap.containsKey(toId)) {
            throw new ArmsAuthException(
                    "Invalid status transition from '" + incident.getStatus().getName()
                    + "' to '" + newStatus.getName() + "'",
                    422
            );
        }

        if (!toMap.get(toId).contains(roleCode)) {
            throw new ArmsAuthException(
                    "You do not have permission to move an incident to '" + newStatus.getName() + "'",
                    403
            );
        }
    }
}
