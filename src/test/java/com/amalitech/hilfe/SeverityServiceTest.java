package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.SeverityRequest;
import com.amalitech.hilfe.dto.UpdateSeveritySlaRequest;
import com.amalitech.hilfe.models.Severity;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.SeverityRepository;
import com.amalitech.hilfe.services.SeverityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeverityServiceTest {

    @Mock SeverityRepository severityRepository;
    @Mock IncidentRepository incidentRepository;
    @InjectMocks SeverityService severityService;

    private Severity severity() {
        Severity s = new Severity();
        s.setId("sev-1");
        s.setName("Low");
        s.setDescription("Low priority");
        s.setStatus(true);
        return s;
    }

    @Test
    void createSeverity_trimsNameAndDescription() {
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.empty());
        when(severityRepository.save(any(Severity.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = severityService.createSeverity(
                new SeverityRequest("  Low  ", "  Low priority  "));

        assertThat(response.name()).isEqualTo("Low");
        assertThat(response.description()).isEqualTo("Low priority");
        var captor = org.mockito.ArgumentCaptor.forClass(Severity.class);
        verify(severityRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Low");
        assertThat(captor.getValue().getDescription()).isEqualTo("Low priority");
    }

    @Test
    void updateSeverity_trimsNameAndDescription() {
        Severity s = severity();
        when(severityRepository.findById("sev-1")).thenReturn(Optional.of(s));
        when(severityRepository.findByNameIgnoreCase("Medium")).thenReturn(Optional.empty());
        when(severityRepository.save(s)).thenReturn(s);

        var response = severityService.updateSeverity("sev-1",
                new SeverityRequest("  Medium  ", "  Medium priority  "));

        assertThat(response.name()).isEqualTo("Medium");
        assertThat(response.description()).isEqualTo("Medium priority");
        assertThat(s.getName()).isEqualTo("Medium");
        assertThat(s.getDescription()).isEqualTo("Medium priority");
    }

    @Test
    void updateSeveritySla_bothFields_updatesBoth() {
        Severity s = severity();
        s.setResponseTimeMinutes(60);
        s.setResolutionTimeMinutes(480);
        when(severityRepository.findById("sev-1")).thenReturn(Optional.of(s));
        when(severityRepository.save(any(Severity.class))).thenAnswer(inv -> inv.getArgument(0));

        severityService.updateSeveritySla("sev-1", new UpdateSeveritySlaRequest(1800, 14400));

        ArgumentCaptor<Severity> captor = ArgumentCaptor.forClass(Severity.class);
        verify(severityRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseTimeMinutes()).isEqualTo(30);
        assertThat(captor.getValue().getResolutionTimeMinutes()).isEqualTo(240);
    }

    @Test
    void updateSeveritySla_onlyResponseTime_leavesResolutionUnchanged() {
        Severity s = severity();
        s.setResponseTimeMinutes(60);
        s.setResolutionTimeMinutes(480);
        when(severityRepository.findById("sev-1")).thenReturn(Optional.of(s));
        when(severityRepository.save(any(Severity.class))).thenAnswer(inv -> inv.getArgument(0));

        severityService.updateSeveritySla("sev-1", new UpdateSeveritySlaRequest(1800, null));

        ArgumentCaptor<Severity> captor = ArgumentCaptor.forClass(Severity.class);
        verify(severityRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseTimeMinutes()).isEqualTo(30);
        assertThat(captor.getValue().getResolutionTimeMinutes()).isEqualTo(480);  // unchanged
    }

    @Test
    void updateSeveritySla_onlyResolutionTime_leavesResponseUnchanged() {
        Severity s = severity();
        s.setResponseTimeMinutes(60);
        s.setResolutionTimeMinutes(480);
        when(severityRepository.findById("sev-1")).thenReturn(Optional.of(s));
        when(severityRepository.save(any(Severity.class))).thenAnswer(inv -> inv.getArgument(0));

        severityService.updateSeveritySla("sev-1", new UpdateSeveritySlaRequest(null, 14400));

        ArgumentCaptor<Severity> captor = ArgumentCaptor.forClass(Severity.class);
        verify(severityRepository).save(captor.capture());
        assertThat(captor.getValue().getResponseTimeMinutes()).isEqualTo(60);    // unchanged
        assertThat(captor.getValue().getResolutionTimeMinutes()).isEqualTo(240);
    }
}
