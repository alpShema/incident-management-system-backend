package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.CacheConfig;
import com.amalitech.hilfe.dto.dashboard.AgentDashboardSummary;
import com.amalitech.hilfe.dto.dashboard.DashboardIncident;
import com.amalitech.hilfe.dto.dashboard.StatusCount;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
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

    @Cacheable(value = CacheConfig.AGENT_DASHBOARD, key = "#userId")
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
}
