package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.dashboard.AgentDashboardCharts;
import com.amalitech.hilfe.dto.dashboard.AgentDashboardStats;
import com.amalitech.hilfe.dto.dashboard.AgentDashboardSummary;
import com.amalitech.hilfe.dto.dashboard.DashboardIncident;
import com.amalitech.hilfe.dto.dashboard.MonthlyCount;
import com.amalitech.hilfe.dto.dashboard.StatusCount;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentDashboardService {

    private static final int RECENTLY_UPDATED_LIMIT = 5;
    private static final int AVG_RESOLUTION_DAYS = 30;

    private final IncidentRepository incidentRepository;
    private final AgentRepository agentRepository;

    public AgentDashboardStats getStats(String userId) {
        String agentId = resolveAgentId(userId);
        List<StatusCount> byStatus = toStatusCount(incidentRepository.countByStatusForAgent(agentId));
        long total    = incidentRepository.countByAssignedToId(agentId);
        long open     = countFor(byStatus, "open");
        long closed   = countFor(byStatus, "closed");
        long resolved = countFor(byStatus, "resolved");
        return new AgentDashboardStats(total, open, closed, resolved);
    }

    public AgentDashboardCharts getCharts(String userId, String period) {
        String agentId = resolveAgentId(userId);
        Instant since      = resolvePeriod(period);
        Instant trendSince = Instant.now().minus(180, ChronoUnit.DAYS);

        List<StatusCount> byStatus = toStatusCount(
                since != null
                        ? incidentRepository.countByStatusForAgentSince(agentId, since)
                        : incidentRepository.countByStatusForAgent(agentId)
        );

        List<MonthlyCount> myTrend = toMonthlyCount(
                incidentRepository.countByMonthForUser(userId, trendSince));

        List<MonthlyCount> assignedTrend = toMonthlyCount(
                incidentRepository.countByMonthForAgent(agentId, trendSince));

        return new AgentDashboardCharts(byStatus, myTrend, assignedTrend);
    }

    public Page<IncidentResponse> getAssignedIncidents(
            String userId,
            String statusId, String severityId, String incidentTypeId, String locationId,
            Pageable pageable
    ) {
        String agentId = resolveAgentId(userId);
        return incidentRepository
                .findByAssignedToIdFiltered(agentId, statusId, severityId, incidentTypeId, locationId, pageable)
                .map(IncidentResponse::from);
    }

    public AgentDashboardSummary getSummary(String userId) {
        String agentId = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ArmsAuthException("Agent record not found for user", 404))
                .getId();

        Instant startOfWeek = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(7, ChronoUnit.DAYS);
        Instant thirtyDaysAgo = Instant.now().minus(AVG_RESOLUTION_DAYS, ChronoUnit.DAYS);

        int totalAssigned = (int) incidentRepository.countByAssignedToId(agentId);

        List<StatusCount> byStatus = incidentRepository.countByStatusForAgent(agentId)
                .stream()
                .map(row -> new StatusCount((String) row[0], ((Long) row[1]).intValue()))
                .toList();

        int highCriticalCount = (int) incidentRepository.countHighCriticalByAgent(agentId);
        int unacknowledgedCount = (int) incidentRepository.countUnacknowledgedByAgent(agentId);
        int closedThisWeek = (int) incidentRepository.countClosedSince(agentId, startOfWeek);

        Double avgResolutionHours = incidentRepository.avgResolutionHoursSince(agentId, thirtyDaysAgo);

        List<DashboardIncident> recentlyUpdated = incidentRepository
                .findRecentlyUpdatedByAgent(agentId, PageRequest.of(0, RECENTLY_UPDATED_LIMIT))
                .stream()
                .map(DashboardIncident::from)
                .toList();

        return new AgentDashboardSummary(
                totalAssigned,
                byStatus,
                highCriticalCount,
                unacknowledgedCount,
                closedThisWeek,
                avgResolutionHours != null ? Math.round(avgResolutionHours * 10.0) / 10.0 : null,
                recentlyUpdated
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveAgentId(String userId) {
        return agentRepository.findByUserId(userId)
                .map(Agent::getId)
                .orElseThrow(() -> new ArmsAuthException("Agent record not found for user", 404));
    }

    private Instant resolvePeriod(String period) {
        if (period == null) return null;
        return switch (period.toLowerCase()) {
            case "7d"  -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default    -> null;
        };
    }

    private long countFor(List<StatusCount> list, String statusName) {
        return list.stream()
                .filter(s -> s.status() != null && s.status().equalsIgnoreCase(statusName))
                .mapToLong(StatusCount::count)
                .sum();
    }

    private List<StatusCount> toStatusCount(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new StatusCount((String) row[0], ((Long) row[1]).intValue()))
                .toList();
    }

    private List<MonthlyCount> toMonthlyCount(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new MonthlyCount((String) row[0], ((Number) row[2]).intValue()))
                .toList();
    }
}
