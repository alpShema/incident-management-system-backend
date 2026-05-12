package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.dashboard.AdminDashboardCharts;
import com.amalitech.hilfe.dto.dashboard.AdminDashboardStats;
import com.amalitech.hilfe.dto.dashboard.LabelCount;
import com.amalitech.hilfe.dto.dashboard.MonthlyCount;
import com.amalitech.hilfe.repositories.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final IncidentRepository incidentRepository;

    public AdminDashboardStats getStats() {
        List<LabelCount> byStatus = toLabel(incidentRepository.countByStatusGlobal());
        long total = byStatus.stream().mapToLong(LabelCount::count).sum();
        long open = countFor(byStatus, "open");
        long closed = countFor(byStatus, "closed");
        long resolved = countFor(byStatus, "resolved");
        return new AdminDashboardStats(total, open, closed, resolved);
    }

    public AdminDashboardCharts getCharts(String period) {
        Instant since = resolvePeriod(period);
        Instant trendSince = Instant.now().minus(180, ChronoUnit.DAYS);

        List<LabelCount> byStatus = toLabel(
                since != null
                        ? incidentRepository.countByStatusSince(since)
                        : incidentRepository.countByStatusGlobal()
        );

        List<MonthlyCount> monthlyTrend = incidentRepository.countByMonthSince(trendSince)
                .stream()
                .map(row -> new MonthlyCount((String) row[0], ((Number) row[2]).intValue()))
                .toList();

        return new AdminDashboardCharts(byStatus, monthlyTrend);
    }

    public Page<IncidentResponse> getMyIncidents(
            String userId,
            String statusId, String severityId, String incidentTypeId, String locationId,
            Pageable pageable
    ) {
        return incidentRepository
                .findByUserIdFiltered(userId, statusId, severityId, incidentTypeId, locationId, pageable)
                .map(IncidentResponse::from);
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

    private long countFor(List<LabelCount> byStatus, String statusName) {
        return byStatus.stream()
                .filter(l -> l.label() != null && l.label().equalsIgnoreCase(statusName))
                .mapToLong(LabelCount::count)
                .sum();
    }

    private List<LabelCount> toLabel(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new LabelCount((String) row[0], ((Long) row[1]).intValue()))
                .toList();
    }
}
