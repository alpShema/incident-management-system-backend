package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.dashboard.AdminDashboardSummary;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @QueryMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "')")
    public AdminDashboardSummary adminDashboard() {
        return dashboardService.getSummary();
    }
}
