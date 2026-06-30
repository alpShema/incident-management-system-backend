package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.IncidentDateFilter;
import com.amalitech.hilfe.dto.IncidentFilterParams;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.dashboard.DashboardCharts;
import com.amalitech.hilfe.dto.dashboard.DashboardStats;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.AgentGroupMemberRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.services.DashboardService;
import com.amalitech.hilfe.services.SlaService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock IncidentRepository incidentRepository;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @Mock SlaService slaService;
    @InjectMocks DashboardService dashboardService;

    private Agent buildAgent(String agentId) {
        return Agent.builder().id(agentId).userId("user-1").build();
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_adminRole_callsCountByStatusGlobal() {
        when(incidentRepository.countByStatusGlobal()).thenReturn(List.of(
                new Object[]{"Open", 10L},
                new Object[]{"In Progress", 3L},
                new Object[]{"Closed", 5L},
                new Object[]{"Resolved", 3L}
        ));

        DashboardStats stats = dashboardService.getStats("admin-1", RoleCode.ADMIN);

        assertThat(stats.totalIncidents()).isEqualTo(21L);
        assertThat(stats.openCount()).isEqualTo(10L);
        assertThat(stats.inProgressCount()).isEqualTo(3L);
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
        assertThat(stats.inProgressCount()).isZero();
        verify(incidentRepository).countByStatusGlobal();
    }

    @Test
    void getStats_agentRole_agentFound_returnsAssignedStats() {
        Agent agent = buildAgent("agent-1");
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.countByStatusForAgent("agent-1")).thenReturn(List.of(
                new Object[]{"Open", 4L},
                new Object[]{"In Progress", 2L},
                new Object[]{"Closed", 2L},
                new Object[]{"Resolved", 1L}
        ));

        DashboardStats stats = dashboardService.getStats("user-1", RoleCode.AGENT);

        assertThat(stats.totalIncidents()).isEqualTo(9L);
        assertThat(stats.openCount()).isEqualTo(4L);
        assertThat(stats.inProgressCount()).isEqualTo(2L);
        assertThat(stats.closedCount()).isEqualTo(2L);
        assertThat(stats.resolvedCount()).isEqualTo(1L);
    }

    @Test
    void getStats_agentRole_agentNotFound_returnsZeroes() {
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        DashboardStats stats = dashboardService.getStats("user-1", RoleCode.AGENT);

        assertThat(stats.totalIncidents()).isZero();
        assertThat(stats.openCount()).isZero();
        assertThat(stats.inProgressCount()).isZero();
        assertThat(stats.closedCount()).isZero();
        assertThat(stats.resolvedCount()).isZero();
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
        when(incidentRepository.countByStatusForAgentSince(eq("agent-1"), any(Instant.class))).thenReturn(List.of());
        when(incidentRepository.countByMonthForUser(eq("user-1"), any(Instant.class))).thenReturn(List.of());
        when(incidentRepository.countByMonthForAgent(eq("agent-1"), any(Instant.class))).thenReturn(List.of());

        DashboardCharts charts = dashboardService.getCharts("user-1", RoleCode.AGENT, null);

        assertThat(charts.trends()).hasSize(2);
        assertThat(charts.trends().get(0).label()).isEqualTo("My Incidents");
        assertThat(charts.trends().get(1).label()).isEqualTo("My Assigned Incidents");
    }

    @Test
    void getCharts_agentRole_agentNotFound_returnsEmptyByStatusAndSeries() {
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());
        when(incidentRepository.countByMonthForUser(eq("user-1"), any(Instant.class))).thenReturn(List.of());

        DashboardCharts charts = dashboardService.getCharts("user-1", RoleCode.AGENT, null);

        assertThat(charts.byStatus()).isEmpty();
        assertThat(charts.trends()).hasSize(2);
        assertThat(charts.trends().get(0).label()).isEqualTo("My Incidents");
        assertThat(charts.trends().get(0).data()).isNotEmpty();
        charts.trends().get(0).data().forEach(mc -> assertThat(mc.count()).isZero());
        assertThat(charts.trends().get(1).label()).isEqualTo("My Assigned Incidents");
        assertThat(charts.trends().get(1).data()).isNotEmpty();
        charts.trends().get(1).data().forEach(mc -> assertThat(mc.count()).isZero());
    }

    @Test
    void getCharts_unsupportedPeriod_throws400() {
        assertThatThrownBy(() -> dashboardService.getCharts("admin-1", RoleCode.ADMIN, "60d"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    // ── getIncidents ──────────────────────────────────────────────────────────

    @Test
    void getIncidents_adminRole_callsFindAllUnified() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findAllUnified(isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class)))
                .thenReturn(page);
        when(slaService.toIncidentResponsePage(page)).thenReturn(new PageImpl<>(List.of()));

        Page<IncidentResponse> result = dashboardService.getIncidents(
                "admin-1", RoleCode.ADMIN, null, new IncidentFilterParams(null, null, null, "cat-it", null), Pageable.unpaged());

        assertThat(result).isNotNull();
        verify(incidentRepository).findAllUnified(isNull(), eq(new IncidentFilterParams(null, null, null, "cat-it", null)), any(IncidentDateFilter.class), any(Pageable.class));
    }

    @Test
    void getIncidents_agentRole_agentFound_callsFindByDepartmentUnified() {
        Agent agent = buildAgent("agent-1");
        Page<Incident> page = new PageImpl<>(List.of());
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-1")).thenReturn(List.of("dept-1"));
        when(incidentRepository.findByDepartmentUnified(anyList(), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class)))
                .thenReturn(page);
        when(slaService.toIncidentResponsePage(page)).thenReturn(new PageImpl<>(List.of()));

        Page<IncidentResponse> result = dashboardService.getIncidents(
                "user-1", RoleCode.AGENT, null, new IncidentFilterParams(null, null, null, null, null), Pageable.unpaged());

        assertThat(result).isNotNull();
        verify(incidentRepository).findByDepartmentUnified(eq(List.of("dept-1")), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class));
    }

    @Test
    void getIncidents_agentRole_agentNotFound_returnsEmptyPage() {
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        Page<IncidentResponse> result = dashboardService.getIncidents(
                "user-1", RoleCode.AGENT, null, new IncidentFilterParams(null, null, null, null, null), Pageable.unpaged());

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void getStats_adminAgentRole_callsCountByStatusGlobal() {
        when(incidentRepository.countByStatusGlobal()).thenReturn(List.of(
                new Object[]{"Open", 4L},
                new Object[]{"Closed", 2L}
        ));

        DashboardStats stats = dashboardService.getStats("aa-1", RoleCode.ADMIN_AGENT);

        assertThat(stats.totalIncidents()).isEqualTo(6L);
        verify(incidentRepository).countByStatusGlobal();
    }

    @Test
    void getCharts_adminAgentRole_returnsAllIncidentsTrendSeries() {
        when(incidentRepository.countByStatusGlobal()).thenReturn(List.of(new Object[]{"Open", 2L}));
        when(incidentRepository.countByMonthSince(any(Instant.class))).thenReturn(List.<Object[]>of());

        DashboardCharts charts = dashboardService.getCharts("aa-1", RoleCode.ADMIN_AGENT, null);

        assertThat(charts.trends()).hasSize(1);
        assertThat(charts.trends().get(0).label()).isEqualTo("All Incidents");
    }

    @Test
    void getIncidents_adminAgentRole_callsFindAllUnified() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findAllUnified(isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class)))
                .thenReturn(page);
        when(slaService.toIncidentResponsePage(page)).thenReturn(new PageImpl<>(List.of()));

        Page<IncidentResponse> result = dashboardService.getIncidents(
                "aa-1", RoleCode.ADMIN_AGENT, null, new IncidentFilterParams(null, null, null, null, null), Pageable.unpaged());

        assertThat(result).isNotNull();
        verify(incidentRepository).findAllUnified(isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class));
    }

    // ── getMyIncidents ────────────────────────────────────────────────────────

    @Test
    void getMyIncidents_callsFindByUserIdFiltered() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findByUserIdFiltered(anyString(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(slaService.toIncidentResponsePage(page)).thenReturn(new PageImpl<>(List.of()));

        Page<IncidentResponse> result = dashboardService.getMyIncidents(
                "user-1", new IncidentFilterParams(null, null, null, null, null), Pageable.unpaged());

        assertThat(result).isNotNull();
        verify(incidentRepository).findByUserIdFiltered(anyString(), any(), any(), any(), any(), any(), any(Pageable.class));
    }
}
