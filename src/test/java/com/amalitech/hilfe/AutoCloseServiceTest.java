package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.AutoCloseConfigResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.Status;
import com.amalitech.hilfe.models.SystemConfig;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.notifications.NotificationEventPublisher;
import com.amalitech.hilfe.notifications.events.IncidentAutoClosedAgentEvent;
import com.amalitech.hilfe.notifications.events.IncidentAutoClosedClientEvent;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.StatusRepository;
import com.amalitech.hilfe.repositories.SystemConfigRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.AutoCloseService;
import com.amalitech.hilfe.services.BusinessHoursResolver;
import com.amalitech.hilfe.utils.BusinessHoursCalculator.BusinessHours;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoCloseServiceTest {

    // 2026-01-15 is a Thursday, 10:30 UTC -- inside the default 08:00-17:30 business window, so
    // an incident "resolved" a few minutes before this within a small window is unambiguously
    // both calendar- and business-hours-overdue, with no dependence on real wall-clock time.
    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T10:30:00Z");
    private static final BusinessHours DEFAULT_HOURS =
            new BusinessHours(ZoneId.of("UTC"), LocalTime.of(8, 0), LocalTime.of(17, 30));

    @Mock SystemConfigRepository systemConfigRepository;
    @Mock IncidentRepository     incidentRepository;
    @Mock StatusRepository       statusRepository;
    @Mock AgentRepository        agentRepository;
    @Mock ActivityLogService     activityLogService;
    @Mock NotificationEventPublisher notificationEventPublisher;
    @Mock BusinessHoursResolver  businessHoursResolver;
    @Mock Clock                  clock;

    @InjectMocks AutoCloseService autoCloseService;

    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(FIXED_NOW);
        lenient().when(businessHoursResolver.resolveByLocationId(any())).thenReturn(DEFAULT_HOURS);
    }

    // ── getConfig ─────────────────────────────────────────────────────────────

    @Test
    void getConfig_configExists_returnsStoredValue() {
        when(systemConfigRepository.findById(AutoCloseService.AUTO_CLOSE_SECONDS_KEY))
                .thenReturn(Optional.of(config("48")));

        AutoCloseConfigResponse response = autoCloseService.getConfig();

        assertThat(response.durationSeconds()).isEqualTo(48);
    }

    @Test
    void getConfig_configMissing_returnsDefault() {
        when(systemConfigRepository.findById(AutoCloseService.AUTO_CLOSE_SECONDS_KEY))
                .thenReturn(Optional.empty());

        AutoCloseConfigResponse response = autoCloseService.getConfig();

        assertThat(response.durationSeconds()).isEqualTo(AutoCloseService.DEFAULT_AUTO_CLOSE_SECONDS);
    }

    // ── updateConfig ──────────────────────────────────────────────────────────

    @Test
    void updateConfig_savesAndReturnsNewValue() {
        when(systemConfigRepository.findById(AutoCloseService.AUTO_CLOSE_SECONDS_KEY))
                .thenReturn(Optional.of(config("72")));

        AutoCloseConfigResponse response = autoCloseService.updateConfig(48);

        assertThat(response.durationSeconds()).isEqualTo(48);
        verify(systemConfigRepository).save(any(SystemConfig.class));
    }

    @Test
    void updateConfig_noExistingRow_createsNewEntry() {
        when(systemConfigRepository.findById(AutoCloseService.AUTO_CLOSE_SECONDS_KEY))
                .thenReturn(Optional.empty());

        autoCloseService.updateConfig(24);

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("24");
    }

    // ── autoCloseResolvedIncidents ────────────────────────────────────────────

    @Test
    void autoClose_noOverdueIncidents_doesNothing() {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("72")));
        when(incidentRepository.findOverdueResolved(any(Instant.class))).thenReturn(List.of());

        autoCloseService.autoCloseResolvedIncidents();

        verify(incidentRepository, never()).save(any());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void autoClose_overdueIncidents_closesEachAndLogs() {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("72")));

        Incident i1 = Incident.builder().id("inc-1").statusId("status-resolved")
                .resolvedAt(FIXED_NOW.minusSeconds(300)).build();
        Incident i2 = Incident.builder().id("inc-2").statusId("status-resolved")
                .resolvedAt(FIXED_NOW.minusSeconds(300)).build();

        when(incidentRepository.findOverdueResolved(any(Instant.class))).thenReturn(List.of(i1, i2));
        when(statusRepository.findByNameIgnoreCase("Closed"))
                .thenReturn(Optional.of(status("status-closed", "Closed")));

        autoCloseService.autoCloseResolvedIncidents();

        assertThat(i1.getStatusId()).isEqualTo("status-closed");
        assertThat(i1.getClosedAt()).isNotNull();
        assertThat(i1.getResolvedAt()).isNull();

        assertThat(i2.getStatusId()).isEqualTo("status-closed");
        assertThat(i2.getClosedAt()).isNotNull();
        assertThat(i2.getResolvedAt()).isNull();

        verify(incidentRepository, times(2)).save(any(Incident.class));
        verify(activityLogService).logIncidentStatusChange(null, "inc-1", "Resolved", "Closed");
        verify(activityLogService).logIncidentStatusChange(null, "inc-2", "Resolved", "Closed");
    }

    // ── HV-1674: reopen window counts down in business hours, not calendar time ────────────────

    @Test
    void autoClose_resolvedFridayEvening_notYetClosedMondayMorning_becauseBusinessHoursWindowStillOpen() {
        // Resolved Friday 16:00 UTC with a 120-minute (7200s) window: only 90 business minutes
        // remain that day (16:00-17:30), so the other 30 roll into Monday, landing the true
        // deadline at Monday 08:30 UTC. A pure-calendar cutoff (Friday 18:00) would already have
        // "expired" over the weekend -- this is exactly the bug HV-1674 fixes.
        Instant resolvedFridayEvening = Instant.parse("2026-01-16T16:00:00Z");
        Instant mondayBeforeDeadline = Instant.parse("2026-01-19T08:15:00Z");
        when(clock.instant()).thenReturn(mondayBeforeDeadline);
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("7200")));

        Incident incident = Incident.builder().id("inc-1").statusId("status-resolved")
                .resolvedAt(resolvedFridayEvening).build();
        when(incidentRepository.findOverdueResolved(any(Instant.class))).thenReturn(List.of(incident));

        autoCloseService.autoCloseResolvedIncidents();

        assertThat(incident.getStatusId()).isEqualTo("status-resolved");
        verify(incidentRepository, never()).save(any());
        verifyNoInteractions(activityLogService, notificationEventPublisher);
    }

    @Test
    void autoClose_resolvedFridayEvening_closesMondayOnceBusinessHoursWindowElapses() {
        // Same incident/window as above, but "now" has moved past the Monday 08:30 UTC
        // business-hours deadline -- the reopen window has genuinely elapsed, so it closes.
        Instant resolvedFridayEvening = Instant.parse("2026-01-16T16:00:00Z");
        Instant mondayAfterDeadline = Instant.parse("2026-01-19T08:45:00Z");
        when(clock.instant()).thenReturn(mondayAfterDeadline);
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("7200")));

        Incident incident = Incident.builder().id("inc-1").statusId("status-resolved")
                .resolvedAt(resolvedFridayEvening).build();
        when(incidentRepository.findOverdueResolved(any(Instant.class))).thenReturn(List.of(incident));
        when(statusRepository.findByNameIgnoreCase("Closed"))
                .thenReturn(Optional.of(status("status-closed", "Closed")));

        autoCloseService.autoCloseResolvedIncidents();

        assertThat(incident.getStatusId()).isEqualTo("status-closed");
        assertThat(incident.getResolvedAt()).isNull();
        verify(incidentRepository).save(incident);
    }

    @Test
    void autoClose_closedStatusNotConfigured_throws500() {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("72")));
        when(incidentRepository.findOverdueResolved(any(Instant.class)))
                .thenReturn(List.of(Incident.builder().id("inc-1")
                        .resolvedAt(FIXED_NOW.minusSeconds(300)).build()));
        when(statusRepository.findByNameIgnoreCase("Closed")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autoCloseService.autoCloseResolvedIncidents())
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Default 'Closed' status not configured")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(500);
    }

    @Test
    void autoClose_usesConfiguredDurationForCutoffCalculation() {
        int durationSeconds = 86400; // 24 hours in seconds
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config(String.valueOf(durationSeconds))));
        when(incidentRepository.findOverdueResolved(any(Instant.class))).thenReturn(List.of());

        autoCloseService.autoCloseResolvedIncidents();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(incidentRepository).findOverdueResolved(cutoffCaptor.capture());

        // "now" comes from the injected Clock, not the wall clock, so this is an exact match.
        assertThat(cutoffCaptor.getValue()).isEqualTo(FIXED_NOW.minusSeconds(durationSeconds));
    }

    // ── notifications ─────────────────────────────────────────────────────────

    @Test
    void autoClose_assignedIncident_notifiesAgent() {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("72")));

        Incident incident = Incident.builder().id("inc-1").statusId("status-resolved")
                .resolvedAt(FIXED_NOW.minusSeconds(300))
                .assignedToId("agent-1")
                .userId("client-1")
                .build();
        incident.setIncidentNo(42);

        Agent agent = Agent.builder().id("agent-1").userId("agent-user-1").build();

        when(incidentRepository.findOverdueResolved(any(Instant.class))).thenReturn(List.of(incident));
        when(statusRepository.findByNameIgnoreCase("Closed"))
                .thenReturn(Optional.of(status("status-closed", "Closed")));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));

        autoCloseService.autoCloseResolvedIncidents();

        verify(notificationEventPublisher).publish(new IncidentAutoClosedAgentEvent("agent-user-1", "inc-1", 42));
    }

    @Test
    void autoClose_incident_notifiesClient() {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("72")));

        Incident incident = Incident.builder().id("inc-1").statusId("status-resolved")
                .resolvedAt(FIXED_NOW.minusSeconds(300))
                .userId("client-user-1")
                .build();
        incident.setIncidentNo(7);

        when(incidentRepository.findOverdueResolved(any(Instant.class))).thenReturn(List.of(incident));
        when(statusRepository.findByNameIgnoreCase("Closed"))
                .thenReturn(Optional.of(status("status-closed", "Closed")));

        autoCloseService.autoCloseResolvedIncidents();

        verify(notificationEventPublisher).publish(new IncidentAutoClosedClientEvent("client-user-1", "inc-1", 7));
    }

    @Test
    void autoClose_unassignedIncident_doesNotSendAgentNotification() {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("72")));

        Incident incident = Incident.builder().id("inc-1").statusId("status-resolved")
                .resolvedAt(FIXED_NOW.minusSeconds(300))
                .userId("client-user-1")
                .build(); // no assignedToId

        when(incidentRepository.findOverdueResolved(any(Instant.class))).thenReturn(List.of(incident));
        when(statusRepository.findByNameIgnoreCase("Closed"))
                .thenReturn(Optional.of(status("status-closed", "Closed")));

        autoCloseService.autoCloseResolvedIncidents();

        verify(notificationEventPublisher, never()).publish(isA(IncidentAutoClosedAgentEvent.class));
        verify(notificationEventPublisher).publish(new IncidentAutoClosedClientEvent("client-user-1", "inc-1", 0));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SystemConfig config(String value) {
        return SystemConfig.builder()
                .key(AutoCloseService.AUTO_CLOSE_SECONDS_KEY)
                .value(value)
                .build();
    }

    private Status status(String id, String name) {
        Status s = new Status();
        s.setId(id);
        s.setName(name);
        return s;
    }
}
