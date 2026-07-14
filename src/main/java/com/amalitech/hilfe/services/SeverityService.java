package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.SeverityRequest;
import com.amalitech.hilfe.dto.SeverityResponse;
import com.amalitech.hilfe.dto.UpdateSeveritySlaRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Severity;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.SeverityRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeverityService {
    private static final String MSG_SEVERITY_NOT_FOUND = "The requested severity level was not found.";

    private final SeverityRepository severityRepository;
    private final IncidentRepository incidentRepository;

    public List<SeverityResponse> listSeverities() {
        return severityRepository.findByStatus(true).stream()
                .map(SeverityResponse::from)
                .toList();
    }

    public List<SeverityResponse> listActiveSeverities() {
        return severityRepository.findByStatus(true).stream()
                .map(SeverityResponse::from)
                .toList();
    }

    public SeverityResponse getSeverity(String id) {
        Severity severity = severityRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException(MSG_SEVERITY_NOT_FOUND, 404));
        return SeverityResponse.from(severity);
    }

    @Transactional
    public SeverityResponse createSeverity(SeverityRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        if (severityRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ArmsAuthException("A severity with this name already exists. Please choose a different name.", 409);
        }
        Severity severity = Severity.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .status(true)
                .build();
        return SeverityResponse.from(severityRepository.save(severity));
    }

    @Transactional
    public SeverityResponse updateSeverity(String id, SeverityRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        Severity severity = severityRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException(MSG_SEVERITY_NOT_FOUND, 404));
        severityRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ArmsAuthException("A severity with this name already exists. Please choose a different name.", 409);
                });
        severity.setName(name);
        severity.setDescription(description);
        return SeverityResponse.from(severityRepository.save(severity));
    }

    @Transactional
    public SeverityResponse updateSeveritySla(String id, UpdateSeveritySlaRequest request) {
        Severity severity = severityRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException(MSG_SEVERITY_NOT_FOUND, 404));
        if (request.responseTimeSeconds() != null) {
            severity.setResponseTimeMinutes(request.responseTimeSeconds() / 60);
        }
        if (request.resolutionTimeSeconds() != null) {
            severity.setResolutionTimeMinutes(request.resolutionTimeSeconds() / 60);
        }
        return SeverityResponse.from(severityRepository.save(severity));
    }

    @Transactional
    public SeverityResponse deactivateSeverity(String id) {
        Severity severity = severityRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException(MSG_SEVERITY_NOT_FOUND, 404));
        severity.setStatus(false);
        return SeverityResponse.from(severityRepository.save(severity));
    }

    @Transactional
    public void deleteSeverity(String id) {
        Severity severity = severityRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException(MSG_SEVERITY_NOT_FOUND, 404));
        if (incidentRepository.existsBySeverityId(id)) {
            throw new ArmsAuthException("This severity cannot be deleted because it is used by existing incidents.", 409);
        }
        severityRepository.delete(severity);
    }
}
