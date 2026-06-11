package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.IncidentDateFilter;
import com.amalitech.hilfe.dto.IncidentFilterParams;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.dashboard.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final SlaService slaService;

    public DashboardStats getStats(String userId, RoleCode role) {
        if (role == RoleCode.AGENT) {
            return agentRepository.findByUserId(userId)
                    .map(agent -> {
                        List<LabelCount> byStatus = toLabel(incidentRepository.countByStatusForAgentCombined(agent.getId(), userId));
                        long total = byStatus.stream().mapToLong(LabelCount::count).sum();
                        return new DashboardStats(total,
                                countFor(byStatus, "open"), countFor(byStatus, "pending"),
                                countFor(byStatus, "in progress"),
                                countFor(byStatus, "closed"), countFor(byStatus, "resolved"));
                    })
                    .orElse(new DashboardStats(0, 0, 0, 0, 0, 0));
        }

        if (role == RoleCode.ADMIN || role == RoleCode.SUPER_ADMIN) {
            List<LabelCount> byStatus = toLabel(incidentRepository.countByStatusGlobal());
            long total = byStatus.stream().mapToLong(LabelCount::count).sum();
            return new DashboardStats(total,
                    countFor(byStatus, "open"), countFor(byStatus, "pending"),
                    countFor(byStatus, "in progress"),
                    countFor(byStatus, "closed"), countFor(byStatus, "resolved"));
        }

        throw new ArmsAuthException("Dashboard not available for this role", 403);
    }

    public DashboardCharts getCharts(String userId, RoleCode role, String period) {
        Instant since      = resolvePeriod(period);
        Instant trendSince = since != null ? since : Instant.now().minus(180, ChronoUnit.DAYS);

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
                var agentOpt = agentRepository.findByUserId(userId);
                byStatus = agentOpt.map(agent -> toLabel(since != null
                        ? incidentRepository.countByStatusForAgentCombinedSince(agent.getId(), userId, since)
                        : incidentRepository.countByStatusForAgentCombined(agent.getId(), userId)))
                        .orElse(List.of());
                List<MonthlyCount> myTrend = agentOpt
                        .map(agent -> toMonthlyCount(incidentRepository.countByMonthForAgentCombined(agent.getId(), userId, trendSince)))
                        .orElse(List.of());
                List<MonthlyCount> assignedTrend = agentOpt
                        .map(agent -> toMonthlyCount(incidentRepository.countByMonthForAgent(agent.getId(), trendSince)))
                        .orElse(List.of());
                trends = List.of(
                        new TrendSeries("My Incidents", myTrend),
                        new TrendSeries("My Assigned Incidents", assignedTrend));
            }
            default -> throw new ArmsAuthException("Dashboard not available for this role", 403);
        }

        return new DashboardCharts(byStatus, trends);
    }

    public Page<IncidentResponse> getIncidents(
            String userId, RoleCode role,
            String query,
            IncidentFilterParams filters,
            Pageable pageable
    ) {
        String queryPattern = null;
        if (query != null && !query.isBlank()) {
            String escaped = query.toLowerCase()
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            queryPattern = "%" + escaped + "%";
        }
        final String finalQueryPattern = queryPattern;

        if (role == RoleCode.ADMIN || role == RoleCode.SUPER_ADMIN) {
            return slaService.toIncidentResponsePage(
                    incidentRepository.findAllUnified(finalQueryPattern, filters, new IncidentDateFilter(null, null), pageable)
            );
        }
        if (role == RoleCode.AGENT) {
            return findAgentGroupIds(userId)
                    .filter(agentGroupIds -> !agentGroupIds.isEmpty())
                    .map(agentGroupIds -> slaService.toIncidentResponsePage(incidentRepository
                            .findByDepartmentUnified(agentGroupIds, finalQueryPattern, filters, new IncidentDateFilter(null, null), pageable)))
                    .orElse(new PageImpl<>(List.of(), pageable, 0));
        }
        throw new ArmsAuthException("Dashboard not available for this role", 403);
    }


    public Page<IncidentResponse> getMyIncidents(
            String userId,
            IncidentFilterParams filters,
            Pageable pageable
    ) {
        return slaService.toIncidentResponsePage(
                incidentRepository.findByUserIdFiltered(userId, filters.statusId(), filters.severityId(), filters.incidentTypeId(), filters.categoryId(), filters.locationId(), pageable)
        );
    }

    public SlaReportResponse getSlaReport(Instant from, Instant to, String severityId) {
        return slaService.getReport(from, to, severityId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Optional<List<String>> findAgentGroupIds(String userId) {
        return agentRepository.findByUserId(userId)
                .map(agent -> agentGroupMemberRepository.findAgentGroupIdsByAgentId(agent.getId()));
    }

    private Instant resolvePeriod(String period) {
        if (period == null) return null;
        return switch (period.toLowerCase()) {
            case "7d"  -> Instant.now().minus(7,  ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default    -> throw new ArmsAuthException(
                    "Unsupported period '" + period + "'. Accepted values: 7d, 30d, 90d.", 400);
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
