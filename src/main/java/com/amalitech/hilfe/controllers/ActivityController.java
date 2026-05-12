package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Activity Logs", description = "Audit trail of all system actions — role changes, incident mutations, assignments")
@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {
    private final ActivityLogService activityLogService;

    @Operation(
        summary = "List activity logs",
        description = "Returns a paginated, reverse-chronological audit log of all system events. Requires `rbac.role.read` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Activity logs retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.RBAC_ROLE_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponse>>> activityLogs(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Activity logs retrieved successfully", PageResponse.from(activityLogService.getActivityLogs(pageable))));
    }
}
