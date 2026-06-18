package com.amalitech.hilfe.slack;

import com.amalitech.hilfe.models.SlackAuditLog;
import com.amalitech.hilfe.repositories.SlackAuditLogRepository;
import com.amalitech.hilfe.slack.service.SlackAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackAuditLogServiceTest {

    @Mock
    private SlackAuditLogRepository auditLogRepository;

    private SlackAuditLogService service;

    @BeforeEach
    void setUp() {
        service = new SlackAuditLogService(auditLogRepository, new ObjectMapper());
    }

    @Test
    void logSync_savesAuditLogWithAllFields() {
        service.logSync("INCIDENT_CREATED", "U_SLACK_001", "hilfe-user-1",
                "INCIDENT", "inc-001", Map.of("incidentNo", "INC-42"));

        ArgumentCaptor<SlackAuditLog> captor = ArgumentCaptor.forClass(SlackAuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        SlackAuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("INCIDENT_CREATED");
        assertThat(saved.getSlackUserId()).isEqualTo("U_SLACK_001");
        assertThat(saved.getHilfeUserId()).isEqualTo("hilfe-user-1");
        assertThat(saved.getResourceType()).isEqualTo("INCIDENT");
        assertThat(saved.getResourceId()).isEqualTo("inc-001");
        assertThat(saved.getMetadata()).contains("incidentNo").contains("INC-42");
    }

    @Test
    void logSync_emptyMetadata_storesEmptyJsonObject() {
        service.logSync("ACTION", "U1", null, "TYPE", null, Map.of());

        ArgumentCaptor<SlackAuditLog> captor = ArgumentCaptor.forClass(SlackAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isEqualTo("{}");
    }

    @Test
    void logSync_nullMetadata_storesEmptyJsonObject() {
        service.logSync("ACTION", "U1", null, "TYPE", null, null);

        ArgumentCaptor<SlackAuditLog> captor = ArgumentCaptor.forClass(SlackAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isEqualTo("{}");
    }

    @Test
    void logSync_repositoryThrows_doesNotPropagate() {
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        assertThatCode(() ->
                service.logSync("ACTION", "U1", null, "TYPE", null, Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void logSync_nullHilfeUserId_isAllowed() {
        service.logSync("CONNECT_INITIATED", "U1", null, "OAUTH", null, Map.of());

        ArgumentCaptor<SlackAuditLog> captor = ArgumentCaptor.forClass(SlackAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getHilfeUserId()).isNull();
    }
}
