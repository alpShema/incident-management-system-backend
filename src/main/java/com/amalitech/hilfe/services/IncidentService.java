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
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import com.amalitech.hilfe.repositories.MediaRepository;
import com.amalitech.hilfe.repositories.SeverityRepository;
import com.amalitech.hilfe.repositories.StatusRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "open",     Set.of("pending"),
            "pending",  Set.of("resolved", "closed"),
            "resolved", Set.of("closed"),
            "closed",   Set.of()
    );

    private final IncidentRepository incidentRepository;
    private final IncidentTypeRepository incidentTypeRepository;
    private final AgentRepository agentRepository;
    private final StatusRepository statusRepository;
    private final SeverityRepository severityRepository;
    private final ActivityLogService activityLogService;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public IncidentResponse createIncident(String userId, CreateIncidentRequest request) {
        if (!incidentTypeRepository.existsById(request.incidentTypeId())) {
            throw new ArmsAuthException("Incident type not found", 404);
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
        return switch (roleCode) {
            case CLIENT -> incidentRepository
                    .findByUserIdFiltered(userId, statusId, severityId, incidentTypeId, categoryId, locationId, pageable)
                    .map(IncidentResponse::from);
            case AGENT -> agentRepository.findByUserId(userId)
                    .map(Agent::getId)
                    .map(agentId -> incidentRepository
                            .findByAssignedToIdFiltered(agentId, statusId, severityId, incidentTypeId, categoryId, locationId, pageable)
                            .map(IncidentResponse::from))
                    .orElse(new PageImpl<>(List.of(), pageable, 0));
            case ADMIN, SUPER_ADMIN -> incidentRepository
                    .findAllFiltered(statusId, severityId, incidentTypeId, categoryId, locationId, pageable)
                    .map(IncidentResponse::from);
        };
    }

    public IncidentResponse getIncident(String incidentId) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
        List<Media> mediaList = mediaRepository.findByIncidentId(incidentId);
        List<MediaResponse> mediaResponses = mediaService.toMediaResponses(mediaList);
        return IncidentResponse.from(incident, mediaResponses);
    }

    @Transactional
    public IncidentResponse updateStatus(String actorUserId, String incidentId, UpdateIncidentStatusRequest request) {
        Incident incident = findIncident(incidentId);

        Status newStatus = statusRepository.findById(request.statusId())
                .orElseThrow(() -> new ArmsAuthException("Status not found", 404));

        enforceTransition(incident, newStatus);

        String previousStatusName = incident.getStatus() != null ? incident.getStatus().getName() : "none";
        incident.setStatusId(request.statusId());
        incident.setClosedAt("closed".equalsIgnoreCase(newStatus.getName()) ? Instant.now() : null);

        incidentRepository.save(incident);
        activityLogService.logIncidentStatusChange(actorUserId, incidentId, previousStatusName, newStatus.getName());
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

        String pendingStatusId = statusRepository.findByNameIgnoreCase("Pending")
                .orElseThrow(() -> new ArmsAuthException("Default 'Pending' status not configured", 500))
                .getId();
        incident.setStatusId(pendingStatusId);

        incidentRepository.save(incident);
        activityLogService.logIncidentAssignment(actorUserId, incidentId, request.agentId());
        entityManager.flush();
        entityManager.clear();
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

    private void enforceTransition(Incident incident, Status newStatus) {
        String currentStatusName = incident.getStatus() != null
                ? incident.getStatus().getName().toLowerCase()
                : null;

        if (currentStatusName == null) return;

        Set<String> allowed = VALID_TRANSITIONS.getOrDefault(currentStatusName, Set.of());
        if (!allowed.contains(newStatus.getName().toLowerCase())) {
            String allowedStr = allowed.isEmpty() ? "none — this is a terminal state" : String.join(", ", allowed);
            throw new ArmsAuthException(
                    "Invalid status transition from '" + incident.getStatus().getName()
                    + "' to '" + newStatus.getName()
                    + "'. Allowed next statuses: " + allowedStr,
                    422
            );
        }
    }
}
