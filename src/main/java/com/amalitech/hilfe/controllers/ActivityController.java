package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {
    private final ActivityLogService activityLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.RBAC_ROLE_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponse>>> activityLogs(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Activity logs retrieved successfully", PageResponse.from(activityLogService.getActivityLogs(pageable))));
    }
}
