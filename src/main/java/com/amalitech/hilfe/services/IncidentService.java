package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AssignIncidentRequest;
import com.amalitech.hilfe.dto.CreateIncidentRequest;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.UpdateIncidentSeverityRequest;
import com.amalitech.hilfe.dto.UpdateIncidentStatusRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentService {
    private final IncidentRepository incidentRepository;
    private final IncidentTypeRepository incidentTypeRepository;
    private final AgentRepository agentRepository;

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
                .severityId(request.severityId())
                .build();

        Incident saved = incidentRepository.save(incident);
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(saved.getId())
                .orElseThrow());
    }

    public Page<IncidentResponse> listIncidents(String userId, RoleCode roleCode, Pageable pageable) {
        return switch (roleCode) {
            case CLIENT -> incidentRepository.findByUserId(userId, pageable).map(IncidentResponse::from);
            case AGENT -> {
                String agentId = agentRepository.findByUserId(userId)
                        .orElseThrow(() -> new ArmsAuthException("Agent record not found for user", 404))
                        .getId();
                yield incidentRepository.findByAssignedToId(agentId, pageable).map(IncidentResponse::from);
            }
            case ADMIN, SUPER_ADMIN -> incidentRepository.findAllWithDetails(pageable).map(IncidentResponse::from);
        };
    }

    public IncidentResponse getIncident(String incidentId) {
        return incidentRepository.findByIdWithDetails(incidentId)
                .map(IncidentResponse::from)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
    }

    @Transactional
    public IncidentResponse updateStatus(String incidentId, UpdateIncidentStatusRequest request) {
        Incident incident = findIncident(incidentId);
        incident.setStatusId(request.statusId());
        incidentRepository.save(incident);
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    @Transactional
    public IncidentResponse updateSeverity(String incidentId, UpdateIncidentSeverityRequest request) {
        Incident incident = findIncident(incidentId);
        incident.setSeverityId(request.severityId());
        incidentRepository.save(incident);
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    @Transactional
    public IncidentResponse assignIncident(String incidentId, AssignIncidentRequest request) {
        Incident incident = findIncident(incidentId);
        incident.setAssignedToId(request.agentId());
        incidentRepository.save(incident);
        return IncidentResponse.from(incidentRepository.findByIdWithDetails(incidentId).orElseThrow());
    }

    private Incident findIncident(String incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ArmsAuthException("Incident not found", 404));
    }
}
