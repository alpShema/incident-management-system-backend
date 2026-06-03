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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoCloseServiceTest {

    @Mock SystemConfigRepository systemConfigRepository;
    @Mock IncidentRepository     incidentRepository;
    @Mock StatusRepository       statusRepository;
    @Mock AgentRepository        agentRepository;
    @Mock ActivityLogService     activityLogService;
    @Mock NotificationEventPublisher notificationEventPublisher;

    @InjectMocks AutoCloseService autoCloseService;

    // ── getConfig ─────────────────────────────────────────────────────────────

    @Test
    void getConfig_configExists_returnsStoredValue() {
        when(systemConfigRepository.findById(AutoCloseService.AUTO_CLOSE_HOURS_KEY))
                .thenReturn(Optional.of(config("48")));

        AutoCloseConfigResponse response = autoCloseService.getConfig();

        assertThat(response.durationHours()).isEqualTo(48);
    }

    @Test
    void getConfig_configMissing_returnsDefault() {
        when(systemConfigRepository.findById(AutoCloseService.AUTO_CLOSE_HOURS_KEY))
                .thenReturn(Optional.empty());

        AutoCloseConfigResponse response = autoCloseService.getConfig();

        assertThat(response.durationHours()).isEqualTo(AutoCloseService.DEFAULT_AUTO_CLOSE_HOURS);
    }

    // ── updateConfig ──────────────────────────────────────────────────────────

    @Test
    void updateConfig_savesAndReturnsNewValue() {
        when(systemConfigRepository.findById(AutoCloseService.AUTO_CLOSE_HOURS_KEY))
                .thenReturn(Optional.of(config("72")));

        AutoCloseConfigResponse response = autoCloseService.updateConfig(48);

        assertThat(response.durationHours()).isEqualTo(48);
        verify(systemConfigRepository).save(any(SystemConfig.class));
    }

    @Test
    void updateConfig_noExistingRow_createsNewEntry() {
        when(systemConfigRepository.findById(AutoCloseService.AUTO_CLOSE_HOURS_KEY))
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
                .resolvedAt(Instant.now().minusSeconds(300)).build();
        Incident i2 = Incident.builder().id("inc-2").statusId("status-resolved")
                .resolvedAt(Instant.now().minusSeconds(300)).build();

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

    @Test
    void autoClose_closedStatusNotConfigured_throws500() {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("72")));
        when(incidentRepository.findOverdueResolved(any(Instant.class)))
                .thenReturn(List.of(Incident.builder().id("inc-1").build()));
        when(statusRepository.findByNameIgnoreCase("Closed")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> autoCloseService.autoCloseResolvedIncidents())
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Default 'Closed' status not configured")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(500);
    }

    @Test
    void autoClose_usesConfiguredDurationForCutoffCalculation() {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("24")));
        when(incidentRepository.findOverdueResolved(any(Instant.class))).thenReturn(List.of());

        autoCloseService.autoCloseResolvedIncidents();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(incidentRepository).findOverdueResolved(cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        Instant expectedCutoff = Instant.now().minusSeconds(24 * 3600);
        // allow 5 seconds of test execution drift
        assertThat(cutoff).isBetween(expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
    }

    // ── notifications ─────────────────────────────────────────────────────────

    @Test
    void autoClose_assignedIncident_notifiesAgent() {
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(config("72")));

        Incident incident = Incident.builder().id("inc-1").statusId("status-resolved")
                .resolvedAt(Instant.now().minusSeconds(300))
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
                .resolvedAt(Instant.now().minusSeconds(300))
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
                .resolvedAt(Instant.now().minusSeconds(300))
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
                .key(AutoCloseService.AUTO_CLOSE_HOURS_KEY)
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
