package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.IncidentDateFilter;
import com.amalitech.hilfe.dto.IncidentFilterParams;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.dashboard.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.Status;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.StatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String NO_DASHBOARD_PERMISSION_MESSAGE = "You do not have permission to view this dashboard.";

    private static final String STATUS_OPEN = "open";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_IN_PROGRESS = "in progress";
    private static final String STATUS_RESOLVED = "resolved";
    private static final String STATUS_CLOSED = "closed";
    private static final String STATUS_UNASSIGNED = "unassigned";

    // Reopened is a transition trigger (IncidentService.applyReopenTransition immediately flips it
    // back to In Progress within the same transaction), never a resting status — excluded here so
    // dashboard charts don't surface a permanent zero-count "Reopened" entry.
    private static final Set<String> DASHBOARD_STATUS_NAMES = Set.of(
            STATUS_OPEN, STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_RESOLVED, STATUS_CLOSED
    );

    // Unassigned is only meaningful to admin-level dashboards -- ADMIN, ADMIN_AGENT, or SUPER_ADMIN --
    // agents never see this card, so it's layered on top of the base set rather than included in it.
    private static final Set<String> ADMIN_DASHBOARD_STATUS_NAMES = Stream.concat(
            DASHBOARD_STATUS_NAMES.stream(), Stream.of(STATUS_UNASSIGNED)
    ).collect(Collectors.toUnmodifiableSet());

    private final IncidentRepository incidentRepository;
    private final AgentRepository agentRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final StatusRepository statusRepository;
    private final SlaService slaService;

    public DashboardStats getStats(String userId, RoleCode role) {
        if (role == RoleCode.AGENT) {
            return agentRepository.findByUserId(userId)
                    .map(agent -> {
                        List<LabelCount> byStatus = toLabel(incidentRepository.countByStatusForAgent(agent.getId()));
                        long total = byStatus.stream().mapToLong(LabelCount::count).sum();
                        return new DashboardStats(total,
                                countFor(byStatus, STATUS_OPEN), countFor(byStatus, STATUS_PENDING),
                                countFor(byStatus, STATUS_IN_PROGRESS),
                                countFor(byStatus, STATUS_CLOSED), countFor(byStatus, STATUS_RESOLVED), 0);
                    })
                    .orElse(new DashboardStats(0, 0, 0, 0, 0, 0, 0));
        }

        if (role == RoleCode.ADMIN || role == RoleCode.ADMIN_AGENT || role == RoleCode.SUPER_ADMIN) {
            List<LabelCount> byStatus = toLabel(incidentRepository.countByStatusGlobal());
            long total = byStatus.stream().mapToLong(LabelCount::count).sum();
            return new DashboardStats(total,
                    countFor(byStatus, STATUS_OPEN), countFor(byStatus, STATUS_PENDING),
                    countFor(byStatus, STATUS_IN_PROGRESS),
                    countFor(byStatus, STATUS_CLOSED), countFor(byStatus, STATUS_RESOLVED),
                    countFor(byStatus, STATUS_UNASSIGNED));
        }

        throw new ArmsAuthException(NO_DASHBOARD_PERMISSION_MESSAGE, 403);
    }

    public DashboardCharts getCharts(String userId, RoleCode role, String period) {
        Instant since      = resolvePeriod(period);
        Instant trendSince = since != null ? since : sixMonthWindowStart();

        List<LabelCount> byStatus;
        List<TrendSeries> trends;

        switch (role) {
            case ADMIN, ADMIN_AGENT, SUPER_ADMIN -> {
                byStatus = fillStatusGaps(toLabel(since != null
                        ? incidentRepository.countByStatusSince(since)
                        : incidentRepository.countByStatusGlobal()), ADMIN_DASHBOARD_STATUS_NAMES);
                List<MonthlyCount> allTrend = fillMonthGaps(
                        toMonthlyCount(incidentRepository.countByMonthSince(trendSince)),
                        trendSince);
                trends = List.of(new TrendSeries("All Incidents", allTrend));
            }
            case AGENT -> {
                var agentOpt = agentRepository.findByUserId(userId);
                byStatus = agentOpt.map(agent -> fillStatusGaps(toLabel(
                        incidentRepository.countByStatusForAgentSince(agent.getId(), trendSince)), DASHBOARD_STATUS_NAMES))
                        .orElseGet(() -> allStatusesZero(DASHBOARD_STATUS_NAMES));
                List<MonthlyCount> myTrend = fillMonthGaps(
                        toMonthlyCount(incidentRepository.countByMonthForUser(userId, trendSince)),
                        trendSince);
                List<MonthlyCount> assignedTrend = agentOpt
                        .map(agent -> fillMonthGaps(
                                toMonthlyCount(incidentRepository.countByMonthForAgent(agent.getId(), trendSince)),
                                trendSince))
                        .orElseGet(() -> fillMonthGaps(List.of(), trendSince));
                trends = List.of(
                        new TrendSeries("My Incidents", myTrend),
                        new TrendSeries("My Assigned Incidents", assignedTrend));
            }
            default -> throw new ArmsAuthException(NO_DASHBOARD_PERMISSION_MESSAGE, 403);
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

        if (role == RoleCode.ADMIN || role == RoleCode.ADMIN_AGENT || role == RoleCode.SUPER_ADMIN) {
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
        throw new ArmsAuthException(NO_DASHBOARD_PERMISSION_MESSAGE, 403);
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

    private Instant sixMonthWindowStart() {
        return YearMonth.now(ZoneOffset.UTC).minusMonths(5).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
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

    private List<MonthlyCount> fillMonthGaps(List<MonthlyCount> data, Instant since) {
        Map<String, Integer> countByMonth = data.stream()
                .collect(Collectors.toMap(MonthlyCount::month, MonthlyCount::count));
        List<MonthlyCount> full = new ArrayList<>();
        YearMonth cursor = YearMonth.from(since.atZone(ZoneOffset.UTC));
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        while (!cursor.isAfter(current)) {
            String label = cursor.format(fmt);
            full.add(new MonthlyCount(label, countByMonth.getOrDefault(label, 0)));
            cursor = cursor.plusMonths(1);
        }
        return full;
    }

    private List<String> dashboardStatusNames(Set<String> statusNames) {
        return statusRepository.findAll().stream()
                .map(Status::getName)
                .filter(name -> name != null && statusNames.contains(name.toLowerCase()))
                .sorted()
                .toList();
    }

    private List<LabelCount> fillStatusGaps(List<LabelCount> counted, Set<String> statusNames) {
        Map<String, Integer> countByName = counted.stream()
                .filter(c -> c.label() != null)
                .collect(Collectors.toMap(c -> c.label().toLowerCase(), LabelCount::count));
        return dashboardStatusNames(statusNames).stream()
                .map(name -> new LabelCount(name, countByName.getOrDefault(name.toLowerCase(), 0)))
                .toList();
    }

    private List<LabelCount> allStatusesZero(Set<String> statusNames) {
        return dashboardStatusNames(statusNames).stream()
                .map(name -> new LabelCount(name, 0))
                .toList();
    }
}
