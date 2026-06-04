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
                .orElseThrow(() -> new ArmsAuthException("Severity not found", 404));
        return SeverityResponse.from(severity);
    }

    @Transactional
    public SeverityResponse createSeverity(SeverityRequest request) {
        if (severityRepository.findByNameIgnoreCase(request.name()).isPresent()) {
            throw new ArmsAuthException("A severity with this name already exists", 409);
        }
        Severity severity = Severity.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .status(true)
                .build();
        return SeverityResponse.from(severityRepository.save(severity));
    }

    @Transactional
    public SeverityResponse updateSeverity(String id, SeverityRequest request) {
        Severity severity = severityRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Severity not found", 404));
        severityRepository.findByNameIgnoreCase(request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ArmsAuthException("A severity with this name already exists", 409);
                });
        severity.setName(request.name());
        severity.setDescription(request.description());
        return SeverityResponse.from(severityRepository.save(severity));
    }

    @Transactional
    public SeverityResponse updateSeveritySla(String id, UpdateSeveritySlaRequest request) {
        Severity severity = severityRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Severity not found", 404));
        severity.setResponseTimeMinutes(request.responseTimeMinutes());
        severity.setResolutionTimeMinutes(request.resolutionTimeMinutes());
        return SeverityResponse.from(severityRepository.save(severity));
    }

    @Transactional
    public SeverityResponse deactivateSeverity(String id) {
        Severity severity = severityRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Severity not found", 404));
        severity.setStatus(false);
        return SeverityResponse.from(severityRepository.save(severity));
    }

    @Transactional
    public void deleteSeverity(String id) {
        Severity severity = severityRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Severity not found", 404));
        if (incidentRepository.existsBySeverityId(id)) {
            throw new ArmsAuthException("Cannot delete severity that is in use by incidents", 409);
        }
        severityRepository.delete(severity);
    }
}
