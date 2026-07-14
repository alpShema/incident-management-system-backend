package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.IncidentSlaResponse;
import com.amalitech.hilfe.dto.SlaConfigResponse;
import com.amalitech.hilfe.dto.UpdateSlaConfigRequest;
import com.amalitech.hilfe.dto.dashboard.SlaReportResponse;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentSla;
import com.amalitech.hilfe.models.Severity;
import com.amalitech.hilfe.models.SystemConfig;
import com.amalitech.hilfe.notifications.NotificationEventPublisher;
import com.amalitech.hilfe.notifications.events.IncidentSlaAtRiskEvent;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentSlaRepository;
import com.amalitech.hilfe.repositories.SeverityRepository;
import com.amalitech.hilfe.repositories.SystemConfigRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.SlaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaServiceTest {

    @SuppressWarnings("java:S8692")
    private static final Instant FIXED_NOW = Instant.now();

    @Mock IncidentSlaRepository incidentSlaRepository;
    @Mock SeverityRepository severityRepository;
    @Mock SystemConfigRepository systemConfigRepository;
    @Mock UserRepository userRepository;
    @Mock AgentRepository agentRepository;
    @Mock NotificationEventPublisher notificationEventPublisher;
    @Mock ActivityLogService activityLogService;

    @InjectMocks SlaService slaService;

    @Test
    void onIncidentCreated_savesThresholdSnapshotAndDeadlines() {
        Instant createdAt = Instant.parse("2026-06-04T10:00:00Z");
        Incident incident = Incident.builder()
                .id("inc-1")
                .severityId("sev-1")
                .createdAt(createdAt)
                .build();
        Severity severity = Severity.builder()
                .id("sev-1")
                .name("High")
                .responseTimeMinutes(60)
                .resolutionTimeMinutes(480)
                .build();
        when(severityRepository.findById("sev-1")).thenReturn(Optional.of(severity));

        slaService.onIncidentCreated(incident);

        ArgumentCaptor<IncidentSla> captor = ArgumentCaptor.forClass(IncidentSla.class);
        verify(incidentSlaRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseThresholdMinutes()).isEqualTo(60);
        assertThat(captor.getValue().getResolutionThresholdMinutes()).isEqualTo(480);
        assertThat(captor.getValue().getResponseDueAt()).isEqualTo(createdAt.plus(Duration.ofMinutes(60)));
        assertThat(captor.getValue().getResolutionDueAt()).isEqualTo(createdAt.plus(Duration.ofMinutes(480)));
    }

    @Test
    void onAgentMessageSent_assignedAgentStopsResponseTimer() {
        Incident incident = Incident.builder()
                .id("inc-1")
                .assignedToId("agent-1")
                .createdAt(FIXED_NOW.minus(Duration.ofMinutes(5)))
                .build();
        IncidentSla sla = IncidentSla.builder()
                .incidentId("inc-1")
                .responseDueAt(FIXED_NOW.plus(Duration.ofMinutes(30)))
                .build();
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(Agent.builder().id("agent-1").userId("agent-user-1").build()));
        when(incidentSlaRepository.findById("inc-1")).thenReturn(Optional.of(sla));

        slaService.onAgentMessageSent(incident, "agent-user-1");

        verify(incidentSlaRepository).save(sla);
        assertThat(sla.getFirstResponseAt()).isNotNull();
        assertThat(sla.getResponseElapsedMs()).isNotNull();
    }

    @Test
    void onStatusChanged_pendingResumeShiftsDeadlines() {
        Instant now = FIXED_NOW;
        Incident incident = Incident.builder().id("inc-1").createdAt(now.minus(Duration.ofHours(1))).build();
        IncidentSla sla = IncidentSla.builder()
                .incidentId("inc-1")
                .responseDueAt(now.plus(Duration.ofMinutes(10)))
                .resolutionDueAt(now.plus(Duration.ofHours(2)))
                .pauseStartedAt(now.minus(Duration.ofMinutes(5)))
                .build();
        Instant originalResponseDueAt = sla.getResponseDueAt();
        Instant originalResolutionDueAt = sla.getResolutionDueAt();
        when(incidentSlaRepository.findById("inc-1")).thenReturn(Optional.of(sla));

        slaService.onStatusChanged(incident, "status-pending", "status-in-progress");

        verify(incidentSlaRepository).save(sla);
        assertThat(sla.getPauseStartedAt()).isNull();
        assertThat(sla.getAccumulatedPauseMs()).isGreaterThan(0);
        assertThat(sla.getResponseDueAt()).isAfter(originalResponseDueAt);
        assertThat(sla.getResolutionDueAt()).isAfter(originalResolutionDueAt);
    }

    @Test
    void onStatusChanged_resolvedLate_marksBreachAndRemainingBudget() {
        Instant now = FIXED_NOW;
        Incident incident = Incident.builder().id("inc-1").createdAt(now.minus(Duration.ofHours(3))).build();
        IncidentSla sla = IncidentSla.builder()
                .incidentId("inc-1")
                .resolutionDueAt(now.minus(Duration.ofMinutes(30)))
                .build();
        when(incidentSlaRepository.findById("inc-1")).thenReturn(Optional.of(sla));

        slaService.onStatusChanged(incident, "status-in-progress", "status-resolved");

        verify(incidentSlaRepository).save(sla);
        assertThat(sla.getResolvedAtSnapshot()).isNotNull();
        assertThat(sla.getResolutionBreachedAt()).isEqualTo(sla.getResolutionDueAt());
        assertThat(sla.getResolutionRemainingMsOnResolve()).isZero();
    }

    @Test
    void onStatusChanged_forceClosedBeforeDeadline_marksResolvedAtSnapshotAndNoBreached() {
        Instant now = FIXED_NOW;
        Incident incident = Incident.builder().id("inc-1").createdAt(now.minus(Duration.ofMinutes(1))).build();
        IncidentSla sla = IncidentSla.builder()
                .incidentId("inc-1")
                .resolutionDueAt(now.plus(Duration.ofMinutes(5)))
                .build();
        when(incidentSlaRepository.findById("inc-1")).thenReturn(Optional.of(sla));

        slaService.onStatusChanged(incident, "status-in-progress", "status-closed");

        verify(incidentSlaRepository).save(sla);
        assertThat(sla.getResolvedAtSnapshot()).isNotNull();
        assertThat(sla.getResolutionBreachedAt()).isNull();
        assertThat(sla.getResolutionRemainingMsOnResolve()).isGreaterThan(0L);
    }

    @Test
    void onStatusChanged_forceClosedAfterDeadline_marksBreached() {
        Instant now = FIXED_NOW;
        Incident incident = Incident.builder().id("inc-1").createdAt(now.minus(Duration.ofHours(3))).build();
        IncidentSla sla = IncidentSla.builder()
                .incidentId("inc-1")
                .resolutionDueAt(now.minus(Duration.ofMinutes(10)))
                .build();
        when(incidentSlaRepository.findById("inc-1")).thenReturn(Optional.of(sla));

        slaService.onStatusChanged(incident, "status-in-progress", "status-closed");

        verify(incidentSlaRepository).save(sla);
        assertThat(sla.getResolvedAtSnapshot()).isNotNull();
        assertThat(sla.getResolutionBreachedAt()).isEqualTo(sla.getResolutionDueAt());
        assertThat(sla.getResolutionRemainingMsOnResolve()).isZero();
    }

    @Test
    void scanAndNotify_responseAtRisk_publishesNotificationsOnce() {
        Instant now = FIXED_NOW;
        Incident incident = Incident.builder()
                .id("inc-1")
                .incidentNo(15)
                .assignedToId("agent-1")
                .build();
        IncidentSla sla = IncidentSla.builder()
                .incidentId("inc-1")
                .incident(incident)
                .responseThresholdMinutes(60)
                .responseDueAt(now.plus(Duration.ofMinutes(10)))
                .build();

        when(systemConfigRepository.findById(SlaService.SLA_AT_RISK_PCT_KEY))
                .thenReturn(Optional.of(SystemConfig.builder().key(SlaService.SLA_AT_RISK_PCT_KEY).value("20").build()));
        when(incidentSlaRepository.findActiveResponseTimers()).thenReturn(List.of(sla));
        when(incidentSlaRepository.findActiveResolutionTimers()).thenReturn(List.of());
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(Agent.builder().id("agent-1").userId("agent-user-1").build()));
        when(userRepository.findActiveAdminUserIds()).thenReturn(List.of("admin-1"));

        slaService.scanAndNotify();

        ArgumentCaptor<IncidentSlaAtRiskEvent> captor = ArgumentCaptor.forClass(IncidentSlaAtRiskEvent.class);
        verify(notificationEventPublisher, times(2)).publish(captor.capture());
        verify(incidentSlaRepository).save(sla);
        assertThat(sla.getResponseAtRiskNotifiedAt()).isNotNull();
        assertThat(captor.getAllValues())
                .extracting(IncidentSlaAtRiskEvent::recipientUserId)
                .containsExactlyInAnyOrder("agent-user-1", "admin-1");
    }

    @Test
    void getIncidentSlaResponse_pausedIncidentFreezesStatusAtPausePoint() {
        Instant pauseStartedAt = FIXED_NOW.minus(Duration.ofMinutes(2));
        IncidentSla sla = IncidentSla.builder()
                .incidentId("inc-1")
                .responseThresholdMinutes(60)
                .responseDueAt(FIXED_NOW.plus(Duration.ofMinutes(1)))
                .pauseStartedAt(pauseStartedAt)
                .build();
        when(systemConfigRepository.findById(SlaService.SLA_AT_RISK_PCT_KEY))
                .thenReturn(Optional.of(SystemConfig.builder().key(SlaService.SLA_AT_RISK_PCT_KEY).value("20").build()));
        when(incidentSlaRepository.findById("inc-1")).thenReturn(Optional.of(sla));

        IncidentSlaResponse response = slaService.getIncidentSlaResponse("inc-1");

        assertThat(response).isNotNull();
        assertThat(response.paused()).isTrue();
        assertThat(response.responseStatus()).isIn("ON_TRACK", "AT_RISK");
    }

    @Test
    void updateConfig_persistsAtRiskPercentage() {
        SlaConfigResponse response = slaService.updateConfig(new UpdateSlaConfigRequest(25));

        verify(systemConfigRepository).save(any(SystemConfig.class));
        assertThat(response.atRiskPct()).isEqualTo(25);
    }

    @Test
    void getReport_computesAveragesAndBreaches() {
        Severity low = Severity.builder().id("sev-low").name("Low").build();
        Incident incident = Incident.builder().id("inc-1").severityId("sev-low").build();
        incident.setSeverity(low);
        IncidentSla sla = IncidentSla.builder()
                .incidentId("inc-1")
                .incident(incident)
                .responseElapsedMs(Duration.ofMinutes(30).toMillis())
                .resolutionElapsedMs(Duration.ofHours(2).toMillis())
                .responseBreachedAt(FIXED_NOW)
                .build();
        when(incidentSlaRepository.findForReport(null, false, null, false, null, false)).thenReturn(List.of(sla));

        SlaReportResponse report = slaService.getReport(null, null, null);

        assertThat(report.trackedIncidents()).isEqualTo(1);
        assertThat(report.responseBreachCount()).isEqualTo(1);
        assertThat(report.averageResponseMinutes()).isEqualTo(30.0);
        assertThat(report.bySeverity()).hasSize(1);
    }
}
