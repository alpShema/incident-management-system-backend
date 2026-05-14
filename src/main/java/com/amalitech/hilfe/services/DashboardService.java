package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.dashboard.DashboardCharts;
import com.amalitech.hilfe.dto.dashboard.DashboardStats;
import com.amalitech.hilfe.dto.dashboard.LabelCount;
import com.amalitech.hilfe.dto.dashboard.MonthlyCount;
import com.amalitech.hilfe.dto.dashboard.TrendSeries;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.RoleCode;
import org.springframework.data.domain.PageImpl;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IncidentRepository incidentRepository;
    private final AgentRepository agentRepository;

    public DashboardStats getStats(String userId, RoleCode role) {
        if (role == RoleCode.AGENT) {
            return findAgentId(userId)
                    .map(agentId -> {
                        List<LabelCount> byStatus = toLabel(incidentRepository.countByStatusForAgent(agentId));
                        long total = incidentRepository.countByAssignedToId(agentId);
                        return new DashboardStats(total, countFor(byStatus, "open"), countFor(byStatus, "closed"), countFor(byStatus, "resolved"));
                    })
                    .orElse(new DashboardStats(0, 0, 0, 0));
        }

        if (role == RoleCode.ADMIN || role == RoleCode.SUPER_ADMIN) {
            List<LabelCount> byStatus = toLabel(incidentRepository.countByStatusGlobal());
            long total = byStatus.stream().mapToLong(LabelCount::count).sum();
            return new DashboardStats(total, countFor(byStatus, "open"), countFor(byStatus, "closed"), countFor(byStatus, "resolved"));
        }

        throw new ArmsAuthException("Dashboard not available for this role", 403);
    }

    public DashboardCharts getCharts(String userId, RoleCode role, String period) {
        Instant since      = resolvePeriod(period);
        Instant trendSince = Instant.now().minus(180, ChronoUnit.DAYS);

        List<LabelCount> byStatus;
        List<TrendSeries> trends;

        switch (role) {
            case ADMIN, SUPER_ADMIN -> {
                byStatus = toLabel(since != null
                        ? incidentRepository.countByStatusSince(since)
                        : incidentRepository.countByStatusGlobal());

                List<MonthlyCount> allTrend = toMonthlyCount(
                        incidentRepository.countByMonthSince(trendSince));
                trends = List.of(new TrendSeries("All Incidents", allTrend));
            }
            case AGENT -> {
                var agentIdOpt = findAgentId(userId);
                byStatus = agentIdOpt.map(agentId -> toLabel(since != null
                        ? incidentRepository.countByStatusForAgentSince(agentId, since)
                        : incidentRepository.countByStatusForAgent(agentId)))
                        .orElse(List.of());

                List<MonthlyCount> myTrend = toMonthlyCount(
                        incidentRepository.countByMonthForUser(userId, trendSince));
                List<MonthlyCount> assignedTrend = agentIdOpt
                        .map(agentId -> toMonthlyCount(incidentRepository.countByMonthForAgent(agentId, trendSince)))
                        .orElse(List.of());
                trends = List.of(
                        new TrendSeries("My Incidents", myTrend),
                        new TrendSeries("Assigned Incidents", assignedTrend)
                );
            }
            default -> throw new ArmsAuthException("Dashboard not available for this role", 403);
        }

        return new DashboardCharts(byStatus, trends);
    }

    public Page<IncidentResponse> getIncidents(
            String userId, RoleCode role,
            String statusId, String severityId, String incidentTypeId, String categoryId, String locationId,
            Pageable pageable
    ) {
        return switch (role) {
            case ADMIN, SUPER_ADMIN -> incidentRepository
                    .findAllFiltered(statusId, severityId, incidentTypeId, categoryId, locationId, pageable)
                    .map(IncidentResponse::from);
            case AGENT -> findAgentId(userId)
                    .map(agentId -> incidentRepository
                            .findByAssignedToIdFiltered(agentId, statusId, severityId, incidentTypeId, categoryId, locationId, pageable)
                            .map(IncidentResponse::from))
                    .orElse(new PageImpl<>(List.of(), pageable, 0));
            default -> throw new ArmsAuthException("Dashboard not available for this role", 403);
        };
    }

    public Page<IncidentResponse> getMyIncidents(
            String userId,
            String statusId, String severityId, String incidentTypeId, String categoryId, String locationId,
            Pageable pageable
    ) {
        return incidentRepository
                .findByUserIdFiltered(userId, statusId, severityId, incidentTypeId, categoryId, locationId, pageable)
                .map(IncidentResponse::from);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Optional<String> findAgentId(String userId) {
        return agentRepository.findByUserId(userId).map(Agent::getId);
    }

    private Instant resolvePeriod(String period) {
        if (period == null) return null;
        return switch (period.toLowerCase()) {
            case "7d"  -> Instant.now().minus(7,  ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default    -> null;
        };
    }

    private long countFor(List<LabelCount> list, String statusName) {
        return list.stream()
                .filter(l -> l.label() != null && l.label().equalsIgnoreCase(statusName))
                .mapToLong(LabelCount::count)
                .sum();
    }

    private List<LabelCount> toLabel(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new LabelCount((String) row[0], ((Long) row[1]).intValue()))
                .toList();
    }

    private List<MonthlyCount> toMonthlyCount(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new MonthlyCount((String) row[0], ((Number) row[2]).intValue()))
                .toList();
    }
}
