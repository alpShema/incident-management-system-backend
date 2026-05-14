package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.dashboard.DashboardCharts;
import com.amalitech.hilfe.dto.dashboard.DashboardStats;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.services.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock IncidentRepository incidentRepository;
    @Mock AgentRepository agentRepository;
    @InjectMocks DashboardService dashboardService;

    private Agent buildAgent(String agentId) {
        return Agent.builder().id(agentId).userId("user-1").build();
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_adminRole_callsCountByStatusGlobal() {
        when(incidentRepository.countByStatusGlobal()).thenReturn(List.of(
                new Object[]{"Open", 10L},
                new Object[]{"Closed", 5L},
                new Object[]{"Resolved", 3L}
        ));

        DashboardStats stats = dashboardService.getStats("admin-1", RoleCode.ADMIN);

        assertThat(stats.totalIncidents()).isEqualTo(18L);
        assertThat(stats.openCount()).isEqualTo(10L);
        assertThat(stats.closedCount()).isEqualTo(5L);
        assertThat(stats.resolvedCount()).isEqualTo(3L);
        verify(incidentRepository).countByStatusGlobal();
    }

    @Test
    void getStats_superAdminRole_callsCountByStatusGlobal() {
        when(incidentRepository.countByStatusGlobal()).thenReturn(List.of(
                new Object[]{"Open", 10L},
                new Object[]{"Closed", 5L},
                new Object[]{"Resolved", 3L}
        ));

        DashboardStats stats = dashboardService.getStats("super-1", RoleCode.SUPER_ADMIN);

        assertThat(stats.totalIncidents()).isEqualTo(18L);
        verify(incidentRepository).countByStatusGlobal();
    }

    @Test
    void getStats_agentRole_agentFound_returnsAgentStats() {
        Agent agent = buildAgent("agent-1");
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.countByStatusForAgent("agent-1")).thenReturn(List.of(
                new Object[]{"Open", 4L},
                new Object[]{"Closed", 2L},
                new Object[]{"Resolved", 1L}
        ));
        when(incidentRepository.countByAssignedToId("agent-1")).thenReturn(7L);

        DashboardStats stats = dashboardService.getStats("user-1", RoleCode.AGENT);

        assertThat(stats.totalIncidents()).isEqualTo(7L);
        assertThat(stats.openCount()).isEqualTo(4L);
        assertThat(stats.closedCount()).isEqualTo(2L);
        assertThat(stats.resolvedCount()).isEqualTo(1L);
    }

    @Test
    void getStats_agentRole_agentNotFound_returnsZeroes() {
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        DashboardStats stats = dashboardService.getStats("user-1", RoleCode.AGENT);

        assertThat(stats.totalIncidents()).isEqualTo(0L);
        assertThat(stats.openCount()).isEqualTo(0L);
        assertThat(stats.closedCount()).isEqualTo(0L);
        assertThat(stats.resolvedCount()).isEqualTo(0L);
    }

    @Test
    void getStats_clientRole_throws403() {
        assertThatThrownBy(() -> dashboardService.getStats("user-1", RoleCode.CLIENT))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    // ── getCharts ─────────────────────────────────────────────────────────────

    @Test
    void getCharts_adminRole_noPeriod_returnsAllIncidentsTrendSeries() {
        List<Object[]> statusData = new java.util.ArrayList<>();
        statusData.add(new Object[]{"Open", 10L});
        when(incidentRepository.countByStatusGlobal()).thenReturn(statusData);
        when(incidentRepository.countByMonthSince(any(Instant.class))).thenReturn(List.<Object[]>of());

        DashboardCharts charts = dashboardService.getCharts("admin-1", RoleCode.ADMIN, null);

        assertThat(charts.trends()).hasSize(1);
        assertThat(charts.trends().get(0).label()).isEqualTo("All Incidents");
    }

    @Test
    void getCharts_agentRole_agentFound_returnsTwoTrendSeries() {
        Agent agent = buildAgent("agent-1");
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.countByStatusForAgent("agent-1")).thenReturn(List.of());
        when(incidentRepository.countByMonthForUser(anyString(), any(Instant.class))).thenReturn(List.of());
        when(incidentRepository.countByMonthForAgent(anyString(), any(Instant.class))).thenReturn(List.of());

        DashboardCharts charts = dashboardService.getCharts("user-1", RoleCode.AGENT, null);

        assertThat(charts.trends()).hasSize(2);
        assertThat(charts.trends().get(0).label()).isEqualTo("My Incidents");
        assertThat(charts.trends().get(1).label()).isEqualTo("Assigned Incidents");
    }

    @Test
    void getCharts_agentRole_agentNotFound_returnsEmptyByStatusAndTwoSeries() {
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(incidentRepository.countByMonthForUser(anyString(), any(Instant.class))).thenReturn(List.of());

        DashboardCharts charts = dashboardService.getCharts("user-1", RoleCode.AGENT, null);

        assertThat(charts.byStatus()).isEmpty();
        assertThat(charts.trends()).hasSize(2);
        assertThat(charts.trends().get(1).data()).isEmpty();
    }

    // ── getIncidents ──────────────────────────────────────────────────────────

    @Test
    void getIncidents_adminRole_callsFindAllFiltered() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findAllFiltered(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<IncidentResponse> result = dashboardService.getIncidents(
                "admin-1", RoleCode.ADMIN, null, null, null, "cat-it", null, Pageable.unpaged());

        assertThat(result).isNotNull();
        verify(incidentRepository).findAllFiltered(any(), any(), any(), eq("cat-it"), any(), any(Pageable.class));
    }

    @Test
    void getIncidents_agentRole_agentFound_callsFindByAssignedToIdFiltered() {
        Agent agent = buildAgent("agent-1");
        Page<Incident> page = new PageImpl<>(List.of());
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.findByAssignedToIdFiltered(anyString(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<IncidentResponse> result = dashboardService.getIncidents(
                "user-1", RoleCode.AGENT, null, null, null, null, null, Pageable.unpaged());

        assertThat(result).isNotNull();
        verify(incidentRepository).findByAssignedToIdFiltered(eq("agent-1"), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void getIncidents_agentRole_agentNotFound_returnsEmptyPage() {
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        Page<IncidentResponse> result = dashboardService.getIncidents(
                "user-1", RoleCode.AGENT, null, null, null, null, null, Pageable.unpaged());

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ── getMyIncidents ────────────────────────────────────────────────────────

    @Test
    void getMyIncidents_callsFindByUserIdFiltered() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findByUserIdFiltered(anyString(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<IncidentResponse> result = dashboardService.getMyIncidents(
                "user-1", null, null, null, null, null, Pageable.unpaged());

        assertThat(result).isNotNull();
        verify(incidentRepository).findByUserIdFiltered(anyString(), any(), any(), any(), any(), any(), any(Pageable.class));
    }
}
