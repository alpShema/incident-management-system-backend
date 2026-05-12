package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.dashboard.AdminDashboardCharts;
import com.amalitech.hilfe.dto.dashboard.AdminDashboardStats;
import com.amalitech.hilfe.dto.dashboard.AdminDashboardSummary;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Dashboard summary endpoints")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @Operation(summary = "Admin dashboard summary", description = "Returns platform-wide incident statistics for admins.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dashboard data retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "')")
    public ResponseEntity<ApiResponse<AdminDashboardSummary>> adminDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard retrieved successfully", dashboardService.getSummary()));
    }

    @Operation(summary = "Admin dashboard stats", description = "Returns top-level incident counts: total, open, closed, resolved.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stats retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/admin/stats")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "')")
    public ResponseEntity<ApiResponse<AdminDashboardStats>> adminStats() {
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved successfully", dashboardService.getStats()));
    }

    @Operation(summary = "Admin dashboard charts", description = "Returns donut (by status) and monthly trend data. period: 7d | 30d | 90d (omit for all time).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chart data retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/admin/charts")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "')")
    public ResponseEntity<ApiResponse<AdminDashboardCharts>> adminCharts(
            @Parameter(description = "Time period filter: 7d, 30d, or 90d. Omit for all time.")
            @RequestParam(required = false) String period
    ) {
        return ResponseEntity.ok(ApiResponse.success("Chart data retrieved successfully", dashboardService.getCharts(period)));
    }
}
