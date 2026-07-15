package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.SeverityRequest;
import com.amalitech.hilfe.dto.SeverityResponse;
import com.amalitech.hilfe.dto.UpdateSeveritySlaRequest;
import com.amalitech.hilfe.services.SeverityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Severities", description = "Incident severity / priority lookup values")
@RestController
@RequestMapping("/severities")
@RequiredArgsConstructor
public class SeverityController {
    private final SeverityService severityService;

    @Operation(summary = "List all active severities", description = "Returns all active severity levels (e.g. Low, Medium, High, Critical).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Severities retrieved")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SeverityResponse>>> listSeverities() {
        return ResponseEntity.ok(ApiResponse.success("Severities retrieved successfully", severityService.listSeverities()));
    }

    @Operation(summary = "Get severity by ID", description = "Returns a specific severity by its ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Severity retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Severity not found")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SeverityResponse>> getSeverity(
            @Parameter(description = "Severity ID") @PathVariable String id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Severity retrieved successfully", severityService.getSeverity(id)));
    }

    @Operation(summary = "Create a severity", description = "Creates a new severity level.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Severity created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    @PostMapping
    @PreAuthorize("hasAuthority('severity.create')")
    public ResponseEntity<ApiResponse<SeverityResponse>> createSeverity(
            @Valid @RequestBody SeverityRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Severity created successfully", severityService.createSeverity(request)));
    }

    @Operation(summary = "Update a severity", description = "Updates an existing severity's name and description.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Severity updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Severity not found")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('severity.update')")
    public ResponseEntity<ApiResponse<SeverityResponse>> updateSeverity(
            @Parameter(description = "Severity ID") @PathVariable String id,
            @Valid @RequestBody SeverityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Severity updated successfully", severityService.updateSeverity(id, request)));
    }

    @Operation(summary = "Update severity SLA thresholds", description = "Updates response and resolution SLA thresholds for a severity.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Severity SLA updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Severity not found")
    @PatchMapping("/{id}/sla")
    @PreAuthorize("hasAuthority('severity.update')")
    public ResponseEntity<ApiResponse<SeverityResponse>> updateSeveritySla(
            @Parameter(description = "Severity ID") @PathVariable String id,
            @Valid @RequestBody UpdateSeveritySlaRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Severity SLA updated successfully", severityService.updateSeveritySla(id, request)));
    }

    @Operation(summary = "Deactivate a severity", description = "Soft-deletes a severity by setting status to false.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Severity deactivated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Severity not found")
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('severity.delete')")
    public ResponseEntity<ApiResponse<SeverityResponse>> deactivateSeverity(
            @Parameter(description = "Severity ID") @PathVariable String id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Severity deactivated successfully", severityService.deactivateSeverity(id)));
    }

    @Operation(summary = "Delete a severity", description = "Permanently deletes a severity if not in use by any incidents.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Severity deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Severity not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Severity is in use")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('severity.delete')")
    public ResponseEntity<Void> deleteSeverity(
            @Parameter(description = "Severity ID") @PathVariable String id
    ) {
        severityService.deleteSeverity(id);
        return ResponseEntity.noContent().build();
    }
}
