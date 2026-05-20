package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AssignIncidentRequest;
import com.amalitech.hilfe.dto.CreateIncidentRequest;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.MediaResponse;
import com.amalitech.hilfe.dto.UpdateIncidentSeverityRequest;
import com.amalitech.hilfe.dto.UpdateIncidentStatusRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.Media;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.Severity;
import com.amalitech.hilfe.models.Status;
import com.amalitech.hilfe.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final AgentRepository agentRepository;
    private final StatusRepository statusRepository;
    private final SeverityRepository severityRepository;
    private final ActivityLogService activityLogService;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;
    private final LocationRepository locationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public IncidentResponse createIncident(String userId, CreateIncidentRequest request) {
        List<String> notFound = new java.util.ArrayList<>();
        if (!incidentTypeRepository.existsById(request.incidentTypeId())) {
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
                .statusId("status-open")
                .build();

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

    public Page<IncidentResponse> listIncidents(
            String userId, RoleCode roleCode,
            String statusId, String severityId, String incidentTypeId, String categoryId, String locationId,
            Pageable pageable
    ) {
        Pageable sortedPageable = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));
        return switch (roleCode) {
            case CLIENT -> incidentRepository
                    .findByUserIdFiltered(userId, statusId, severityId, incidentTypeId, categoryId, locationId, sortedPageable)
                    .map(IncidentResponse::from);
            case AGENT -> agentRepository.findByUserId(userId)
                    .map(Agent::getId)
                    .map(agentId -> incidentRepository
                            .findByAgentScope(userId, agentId, statusId, severityId, incidentTypeId, categoryId, locationId, sortedPageable)
                            .map(IncidentResponse::from))
                    .orElse(new PageImpl<>(List.of(), sortedPageable, 0));
            case ADMIN, SUPER_ADMIN -> incidentRepository
                    .findAllFiltered(statusId, severityId, incidentTypeId, categoryId, locationId, sortedPageable)
                    .map(IncidentResponse::from);
        };
    }

    public Page<IncidentResponse> searchIncidents(
            String userId, RoleCode roleCode, String query, Pageable pageable
    ) {
        if (query == null || query.isBlank()) {
            throw new ArmsAuthException("Search query must not be blank", 400);
        }

        String escaped = query.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String queryPattern = "%" + escaped + "%";

        return switch (roleCode) {
            case CLIENT -> incidentRepository
                    .searchByUserId(userId, queryPattern, pageable)
                    .map(IncidentResponse::from);
            case AGENT -> agentRepository.findByUserId(userId)
                    .map(Agent::getId)
                    .map(agentId -> incidentRepository
                            .searchByAgentScope(userId, agentId, queryPattern, pageable)
                            .map(IncidentResponse::from))
                    .orElse(new PageImpl<>(List.of(), pageable, 0));
            case ADMIN, SUPER_ADMIN -> incidentRepository
                    .searchAll(queryPattern, pageable)
                    .map(IncidentResponse::from);
        };
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

        String previousStatusName = incident.getStatus() != null ? incident.getStatus().getName() : "none";
        incident.setStatusId(request.statusId());
        incident.setClosedAt("closed".equalsIgnoreCase(newStatus.getName()) ? Instant.now() : null);

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
        if (roleCode == RoleCode.AGENT) {
            boolean isAssigned = agentRepository.findByUserId(userId)
                    .map(agent -> agent.getId().equals(incident.getAssignedToId()))
                    .orElse(false);
            if (isAssigned) return;
        }
        throw new ArmsAuthException("You do not have access to this incident", 403);
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
        incidentRepository.save(incident);
        activityLogService.logIncidentStatusChange(actorUserId, incidentId, "Reopened", "In Progress");
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

        Set<RoleCode> permittedRoles = VALID_TRANSITIONS
                .getOrDefault(fromId, Map.of())
                .getOrDefault(toId, Set.of());

        if (!permittedRoles.contains(roleCode)) {
            throw new ArmsAuthException(
                    "Invalid status transition from '" + incident.getStatus().getName()
                    + "' to '" + newStatus.getName() + "'",
                    422
            );
        }
    }
}
