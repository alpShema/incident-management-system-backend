package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.dto.AssignIncidentRequest;
import com.amalitech.hilfe.dto.CreateIncidentRequest;
import com.amalitech.hilfe.dto.IncidentDateFilter;
import com.amalitech.hilfe.dto.IncidentFilterParams;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateIncidentSeverityRequest;
import com.amalitech.hilfe.dto.UpdateIncidentStatusRequest;
import com.amalitech.hilfe.security.authorization.CurrentUserAuthority;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.JwtTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.Instant;

@Controller
@RequiredArgsConstructor
public class IncidentResolver {

    private final IncidentService incidentService;
    private final ActivityLogService activityLogService;

    @QueryMapping
    @PreAuthorize("hasAuthority('dashboard.admin')")
    public PageResponse<IncidentResponse> incidents(
            @Argument IncidentFilterParams filter,
            @Argument Instant fromDate,
            @Argument Instant toDate,
            @Argument String query,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                incidentService.queryAllIncidents(query, orEmpty(filter),
                        toDateFilter(fromDate, toDate), PageInput.toPageable(page))
        );
    }

    @QueryMapping
    public PageResponse<IncidentResponse> myIncidents(
            @Argument IncidentFilterParams filter,
            @Argument Instant fromDate,
            @Argument Instant toDate,
            @Argument String query,
            @Argument PageInput page,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return PageInput.toPageResponse(
                incidentService.queryIncidents(principal.userId(), query, orEmpty(filter),
                        toDateFilter(fromDate, toDate), PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAnyAuthority('dashboard.admin', 'dashboard.agent')")
    public PageResponse<IncidentResponse> deptIncidents(
            @Argument IncidentFilterParams filter,
            @Argument Instant fromDate,
            @Argument Instant toDate,
            @Argument String query,
            @Argument PageInput page,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return PageInput.toPageResponse(
                incidentService.queryDeptIncidents(principal.userId(), query, orEmpty(filter),
                        toDateFilter(fromDate, toDate), PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAnyAuthority('dashboard.admin', 'dashboard.agent')")
    public PageResponse<IncidentResponse> assignedIncidents(
            @Argument IncidentFilterParams filter,
            @Argument Instant fromDate,
            @Argument Instant toDate,
            @Argument String query,
            @Argument PageInput page,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return PageInput.toPageResponse(
                incidentService.queryAssignedIncidents(principal.userId(), query, orEmpty(filter),
                        toDateFilter(fromDate, toDate), PageInput.toPageable(page))
        );
    }

    @QueryMapping
    public PageResponse<IncidentResponse> searchIncidents(
            @Argument String query,
            @Argument Instant fromDate,
            @Argument Instant toDate,
            @Argument PageInput page,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return PageInput.toPageResponse(
                incidentService.searchIncidents(principal.userId(), query, fromDate, toDate, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    public IncidentResponse incident(
            @Argument String id,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return incidentService.getIncident(principal.userId(), principal.roleCode(), id);
    }

    @QueryMapping
    public PageResponse<ActivityLogResponse> incidentHistory(
            @Argument String id,
            @Argument PageInput page,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return PageInput.toPageResponse(
                activityLogService.getActivityLogs(id, PageInput.toPageable(page),
                        principal.userId(), principal.roleCode())
        );
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident.create')")
    public IncidentResponse createIncident(
            @Valid @Argument CreateIncidentRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Incident created successfully");
        return incidentService.createIncident(principal.userId(), input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident.status.change')")
    public IncidentResponse updateIncidentStatus(
            @Argument String id,
            @Valid @Argument UpdateIncidentStatusRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        boolean hasForceClose = CurrentUserAuthority.has(RbacPermissions.INCIDENT_FORCECLOSE);
        GraphQlResponseMessage.set("Incident status updated successfully");
        return incidentService.updateStatus(principal.userId(), principal.roleCode(), hasForceClose, id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident.severity.change')")
    public IncidentResponse updateIncidentSeverity(
            @Argument String id,
            @Valid @Argument UpdateIncidentSeverityRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        boolean hasUpdateAny = CurrentUserAuthority.has(RbacPermissions.INCIDENT_UPDATE_ANY);
        GraphQlResponseMessage.set("Incident severity updated successfully");
        return incidentService.updateSeverity(principal.userId(), hasUpdateAny, id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident.assign')")
    public IncidentResponse assignIncident(
            @Argument String id,
            @Valid @Argument AssignIncidentRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        boolean hasUpdateAny = CurrentUserAuthority.has(RbacPermissions.INCIDENT_UPDATE_ANY);
        GraphQlResponseMessage.set("Incident assigned successfully");
        return incidentService.assignIncident(principal.userId(), hasUpdateAny, id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "', '" + RbacPermissions.DASHBOARD_AGENT + "')")
    public IncidentResponse markIncidentRead(
            @Argument String id,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Incident marked as read successfully");
        return incidentService.updateReadStatus(principal.userId(), principal.roleCode(), id, true);
    }

    @MutationMapping
    @PreAuthorize("hasAnyAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "', '" + RbacPermissions.DASHBOARD_AGENT + "')")
    public IncidentResponse markIncidentUnread(
            @Argument String id,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Incident marked as unread successfully");
        return incidentService.updateReadStatus(principal.userId(), principal.roleCode(), id, false);
    }

    private static IncidentFilterParams orEmpty(IncidentFilterParams f) {
        return f != null ? f : new IncidentFilterParams(null, null, null, null, null);
    }

    private static IncidentDateFilter toDateFilter(Instant fromDate, Instant toDate) {
        return new IncidentDateFilter(fromDate, fromDate != null, toDate, toDate != null);
    }
}
