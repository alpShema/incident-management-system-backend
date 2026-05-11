package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.AssignIncidentRequest;
import com.amalitech.hilfe.dto.CreateIncidentRequest;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateIncidentSeverityRequest;
import com.amalitech.hilfe.dto.UpdateIncidentStatusRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.JwtTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentService incidentService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_CREATE + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Valid @RequestBody CreateIncidentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Incident created successfully", incidentService.createIncident(principal.userId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> listIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            Pageable pageable
    ) {
        Page<IncidentResponse> page = incidentService.listIncidents(
                principal.userId(), principal.roleCode(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Incidents retrieved successfully", PageResponse.from(page)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncidentResponse>> getIncident(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Incident retrieved successfully", incidentService.getIncident(id)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_STATUS_CHANGE + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateIncidentStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident status updated successfully", incidentService.updateStatus(id, request)));
    }

    @PatchMapping("/{id}/severity")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_SEVERITY_CHANGE + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateSeverity(
            @PathVariable String id,
            @Valid @RequestBody UpdateIncidentSeverityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident severity updated successfully", incidentService.updateSeverity(id, request)));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_ASSIGN + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> assignIncident(
            @PathVariable String id,
            @Valid @RequestBody AssignIncidentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident assigned successfully", incidentService.assignIncident(id, request)));
    }
}
