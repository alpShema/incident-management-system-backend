package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Activity Logs", description = "Audit trail of all system actions — role changes, incident mutations, assignments")
@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {
    private final ActivityLogService activityLogService;

    @Operation(
        summary = "List activity logs",
        description = "Returns a paginated, reverse-chronological audit log. Without `incidentId`, returns all system events and requires `rbac.role.read` (admin only). "
                    + "With `incidentId`, returns only that incident's activity and is accessible to the incident's reporter, assignee, or any admin."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Activity logs retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponse>>> activityLogs(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Filter logs by incident ID. Required for non-admin callers.")
            @RequestParam(required = false) String incidentId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                "Activity logs retrieved successfully",
                PageResponse.from(activityLogService.getActivityLogs(incidentId, pageable, principal.userId(), principal.roleCode()))
        ));
    }
}
