package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.CacheConfig;
import com.amalitech.hilfe.dto.dashboard.AdminDashboardSummary;
import com.amalitech.hilfe.dto.dashboard.AgentWorkload;
import com.amalitech.hilfe.dto.dashboard.LabelCount;
import com.amalitech.hilfe.dto.dashboard.RecentActivity;
import com.amalitech.hilfe.repositories.ActivityLogRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final IncidentRepository incidentRepository;
    private final ActivityLogRepository activityLogRepository;

    @Cacheable(CacheConfig.ADMIN_DASHBOARD)
    public AdminDashboardSummary getSummary() {
        long totalTickets = incidentRepository.countTotal();
        long unassignedCount = incidentRepository.countUnassigned();

        List<LabelCount> byStatus = toLabel(incidentRepository.countByStatusGlobal());
        List<LabelCount> byCategory = toLabel(incidentRepository.countByCategory());
        List<LabelCount> byTopic = toLabel(incidentRepository.countByTopic());

        List<AgentWorkload> agentWorkload = incidentRepository.countByAgent().stream()
                .map(row -> new AgentWorkload((String) row[0], ((Long) row[1]).intValue()))
                .toList();

        List<RecentActivity> recentActivity = activityLogRepository
                .findRecent(PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
                .stream()
                .map(a -> new RecentActivity(
                        a.actorUserId(),
                        a.action(),
                        a.subjectType(),
                        a.subjectId(),
                        a.description(),
                        a.createdAt().toString()
                ))
                .toList();

        return new AdminDashboardSummary(
                totalTickets,
                unassignedCount,
                0,
                byStatus,
                byCategory,
                byTopic,
                agentWorkload,
                recentActivity
        );
    }

    private List<LabelCount> toLabel(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new LabelCount((String) row[0], ((Long) row[1]).intValue()))
                .toList();
    }
}
