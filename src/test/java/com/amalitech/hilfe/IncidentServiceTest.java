package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.AssignIncidentRequest;
import com.amalitech.hilfe.dto.AttachmentRef;
import com.amalitech.hilfe.dto.CreateIncidentRequest;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.MediaResponse;
import com.amalitech.hilfe.dto.UpdateIncidentSeverityRequest;
import com.amalitech.hilfe.dto.UpdateIncidentStatusRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.Media;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.Severity;
import com.amalitech.hilfe.models.Status;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import com.amalitech.hilfe.repositories.LocationRepository;
import com.amalitech.hilfe.repositories.MediaRepository;
import com.amalitech.hilfe.repositories.SeverityRepository;
import com.amalitech.hilfe.repositories.StatusRepository;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.MediaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock IncidentRepository incidentRepository;
    @Mock IncidentTypeRepository incidentTypeRepository;
    @Mock LocationRepository locationRepository;
    @Mock AgentRepository agentRepository;
    @Mock StatusRepository statusRepository;
    @Mock SeverityRepository severityRepository;
    @Mock ActivityLogService activityLogService;
    @Mock MediaService mediaService;
    @Mock MediaRepository mediaRepository;
    @Mock EntityManager entityManager;
    @InjectMocks IncidentService incidentService;

    @BeforeEach
    void injectEntityManager() {
        ReflectionTestUtils.setField(incidentService, "entityManager", entityManager);
    }

    private Incident buildIncident() {
        Incident incident = Incident.builder()
                .id("inc-1")
                .title("Test Incident")
                .description("Test description")
                .userId("user-1")
                .locationId("loc-1")
                .incidentTypeId("type-1")
                .statusId("status-open")
                .build();
        incident.setIncidentNo(1);
        return incident;
    }

    private Status buildStatus(String id, String name) {
        Status s = new Status();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private Severity buildSeverity(String id, String name) {
        Severity severity = new Severity();
        severity.setId(id);
        severity.setName(name);
        return severity;
    }

    // ── createIncident ────────────────────────────────────────────────────────

    @Test
    void createIncident_happyPath_returnsIncidentResponse() {
        Incident incident = buildIncident();
        when(incidentTypeRepository.existsById("type-1")).thenReturn(true);
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));

        CreateIncidentRequest request = new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null);
        IncidentResponse response = incidentService.createIncident("user-1", request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("inc-1");
        assertThat(response.attachments()).isEmpty();
        var incidentCaptor = forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        assertThat(incidentCaptor.getValue().getSeverityId()).isEqualTo("sev-low");
        verify(entityManager).flush();
    }

    @Test
    void createIncident_withExplicitPriority_usesProvidedSeverityId() {
        Incident incident = buildIncident();
        when(incidentTypeRepository.existsById("type-1")).thenReturn(true);
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.existsById("sev-high")).thenReturn(true);
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));

        CreateIncidentRequest request = new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", "sev-high", null);

        incidentService.createIncident("user-1", request);

        var incidentCaptor = forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        assertThat(incidentCaptor.getValue().getSeverityId()).isEqualTo("sev-high");
    }

    @Test
    void createIncident_withUnknownPriority_throws404() {
        when(incidentTypeRepository.existsById("type-1")).thenReturn(true);
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.existsById("bad-sev")).thenReturn(false);

        CreateIncidentRequest request = new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", "bad-sev", null);

        assertThatThrownBy(() -> incidentService.createIncident("user-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Severity not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);

        verify(incidentRepository, never()).save(any(Incident.class));
    }

    @Test
    void createIncident_defaultLowPriorityMissing_throws500() {
        when(incidentTypeRepository.existsById("type-1")).thenReturn(true);
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.empty());

        CreateIncidentRequest request = new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null);

        assertThatThrownBy(() -> incidentService.createIncident("user-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Default 'Low' priority not configured")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(500);
    }

    @Test
    void createIncident_withAttachments_persistsMediaAndReturnsAttachmentResponses() {
        Incident incident = buildIncident();
        AttachmentRef attachment = new AttachmentRef(
                "media/uuid/photo.png", "photo.png", "image/png", 2048L);
        Media media = Media.builder()
                .id("media-1")
                .incidentId("inc-1")
                .originalName("photo.png")
                .fileKey("media/uuid/photo.png")
                .url("s3://test-bucket/media/uuid/photo.png")
                .contentType("image/png")
                .fileSize(2048L)
                .build();
        MediaResponse mediaResponse = new MediaResponse(
                "media-1", "photo.png", "image/png", 2048L, "https://s3.example.com/get");

        when(incidentTypeRepository.existsById("type-1")).thenReturn(true);
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(mediaService.createMediaForIncident("inc-1", List.of(attachment))).thenReturn(List.of(media));
        when(mediaService.toMediaResponses(List.of(media))).thenReturn(List.of(mediaResponse));
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));

        CreateIncidentRequest request = new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, List.of(attachment));
        IncidentResponse response = incidentService.createIncident("user-1", request);

        assertThat(response.id()).isEqualTo("inc-1");
        assertThat(response.attachments()).containsExactly(mediaResponse);
        verify(mediaService).createMediaForIncident("inc-1", List.of(attachment));
        verify(mediaService).toMediaResponses(List.of(media));
        verify(entityManager, times(2)).flush();
    }

    @Test
    void createIncident_incidentTypeNotFound_throws404() {
        when(incidentTypeRepository.existsById("bad-type")).thenReturn(false);
        when(locationRepository.existsById("loc-1")).thenReturn(true);

        CreateIncidentRequest request = new CreateIncidentRequest(
                "Title", "Desc", "bad-type", "loc-1", null, null);

        assertThatThrownBy(() -> incidentService.createIncident("user-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident type with the provided ID could not be found.")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void createIncident_locationNotFound_throws404() {
        when(incidentTypeRepository.existsById("type-1")).thenReturn(true);
        when(locationRepository.existsById("bad-loc")).thenReturn(false);

        CreateIncidentRequest request = new CreateIncidentRequest(
                "Title", "Desc", "type-1", "bad-loc", null, null);

        assertThatThrownBy(() -> incidentService.createIncident("user-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Location with the provided ID could not be found.")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void createIncident_bothIdsNotFound_throws404WithBothMessages() {
        when(incidentTypeRepository.existsById("bad-type")).thenReturn(false);
        when(locationRepository.existsById("bad-loc")).thenReturn(false);

        CreateIncidentRequest request = new CreateIncidentRequest(
                "Title", "Desc", "bad-type", "bad-loc", null, null);

        assertThatThrownBy(() -> incidentService.createIncident("user-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Incident type with the provided ID could not be found.")
                .hasMessageContaining("Location with the provided ID could not be found.")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // ── listIncidents ─────────────────────────────────────────────────────────

    @Test
    void listIncidents_clientRole_callsFindByUserIdFiltered() {
        Incident incident = buildIncident();
        Page<Incident> page = new PageImpl<>(List.of(incident));
        when(incidentRepository.findByUserIdFiltered(anyString(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.listIncidents(
                "user-1", RoleCode.CLIENT, null, null, null, null, null, Pageable.unpaged());

        assertThat(result).isNotNull();
        verify(incidentRepository).findByUserIdFiltered(anyString(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void listIncidents_agentRole_callsFindByAgentScope() {
        Page<Incident> page = new PageImpl<>(List.of());
        Agent agent = Agent.builder().id("agent-row-1").userId("agent-user-1").build();
        when(agentRepository.findByUserId("agent-user-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.findByAgentScope(anyString(), anyString(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        incidentService.listIncidents(
                "agent-user-1", RoleCode.AGENT, null, null, "type-fire", "cat-facility", null, Pageable.unpaged());

        verify(incidentRepository).findByAgentScope(
                eq("agent-user-1"), eq("agent-row-1"), any(), any(), eq("type-fire"), eq("cat-facility"), any(), any(Pageable.class));
    }

    @Test
    void listIncidents_agentRoleWithoutAgentRecord_returnsEmptyPage() {
        when(agentRepository.findByUserId("agent-user-1")).thenReturn(Optional.empty());

        Page<IncidentResponse> result = incidentService.listIncidents(
                "agent-user-1", RoleCode.AGENT, null, null, null, null, null, Pageable.unpaged());

        assertThat(result).isEmpty();
    }

    @Test
    void listIncidents_adminRole_callsFindAllFiltered() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findAllFiltered(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        incidentService.listIncidents("admin-1", RoleCode.ADMIN, null, null, null, "cat-it", null, Pageable.unpaged());

        verify(incidentRepository).findAllFiltered(any(), any(), any(), eq("cat-it"), any(), any(Pageable.class));
    }

    @Test
    void listIncidents_superAdminRole_callsFindAllFiltered() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findAllFiltered(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        incidentService.listIncidents("super-1", RoleCode.SUPER_ADMIN, null, null, null, null, null, Pageable.unpaged());

        verify(incidentRepository).findAllFiltered(any(), any(), any(), any(), any(), any(Pageable.class));
    }

    // ── getIncident ───────────────────────────────────────────────────────────

    @Test
    void getIncident_found_returnsResponse() {
        Incident incident = buildIncident();
        Media media = Media.builder()
                .id("media-1")
                .incidentId("inc-1")
                .originalName("doc.pdf")
                .fileKey("media/uuid/doc.pdf")
                .url("s3://test-bucket/media/uuid/doc.pdf")
                .contentType("application/pdf")
                .fileSize(5000L)
                .build();
        MediaResponse mediaResponse = new MediaResponse(
                "media-1", "doc.pdf", "application/pdf", 5000L, "https://s3.example.com/get");

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(mediaRepository.findByIncidentId("inc-1")).thenReturn(List.of(media));
        when(mediaService.toMediaResponses(List.of(media))).thenReturn(List.of(mediaResponse));

        IncidentResponse response = incidentService.getIncident("inc-1");

        assertThat(response.id()).isEqualTo("inc-1");
        assertThat(response.attachments()).containsExactly(mediaResponse);
    }

    @Test
    void getIncident_notFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncident("missing"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_validTransition_openToPending_savesAndLogs() {
        Status openStatus = buildStatus("status-open", "open");
        Status pendingStatus = buildStatus("status-pending", "pending");

        Incident incident = buildIncident();
        incident.setStatus(openStatus);

        when(incidentRepository.findByIdWithDetails("inc-1"))
                .thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", "inc-1", new UpdateIncidentStatusRequest("status-pending"));

        verify(incidentRepository).save(any(Incident.class));
        verify(activityLogService).logIncidentStatusChange("actor-1", "inc-1", "open", "pending");
    }

    @Test
    void updateStatus_invalidTransition_closedToOpen_throws422() {
        Status closedStatus = buildStatus("status-closed", "closed");
        Status openStatus = buildStatus("status-open", "open");

        Incident incident = buildIncident();
        incident.setStatus(closedStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-open")).thenReturn(Optional.of(openStatus));

        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", "inc-1", new UpdateIncidentStatusRequest("status-open")))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(422);
    }

    @Test
    void updateStatus_statusNotFound_throws404() {
        Incident incident = buildIncident();
        incident.setStatus(buildStatus("status-open", "open"));

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("bad-status")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", "inc-1", new UpdateIncidentStatusRequest("bad-status")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Status not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateStatus_incidentNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", "missing", new UpdateIncidentStatusRequest("status-pending")))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateStatus_transitionToClosed_setsClosedAt() {
        Status pendingStatus = buildStatus("status-pending", "pending");
        Status closedStatus = buildStatus("status-closed", "closed");

        Incident incident = buildIncident();
        incident.setStatus(pendingStatus);

        when(incidentRepository.findByIdWithDetails("inc-1"))
                .thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-closed")).thenReturn(Optional.of(closedStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", "inc-1", new UpdateIncidentStatusRequest("status-closed"));

        assertThat(incident.getClosedAt()).isNotNull();
        verify(incidentRepository).save(any(Incident.class));
    }

    // ── updateSeverity ────────────────────────────────────────────────────────

    @Test
    void updateSeverity_happyPath_savesAndLogs() {
        Incident incident = buildIncident();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateSeverity("actor-1", "inc-1", new UpdateIncidentSeverityRequest("sev-high"));

        verify(incidentRepository).save(any(Incident.class));
        verify(activityLogService).logIncidentSeverityChange("actor-1", "inc-1", "none", "sev-high");
    }

    @Test
    void updateSeverity_incidentNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                incidentService.updateSeverity("actor-1", "missing", new UpdateIncidentSeverityRequest("sev-high")))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // ── assignIncident ────────────────────────────────────────────────────────

    @Test
    void assignIncident_happyPath_setsAgentAndStatusAndLogs() {
        Incident incident = buildIncident();
        Status pendingStatus = buildStatus("status-pending", "Pending");

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findByNameIgnoreCase("Pending")).thenReturn(Optional.of(pendingStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.assignIncident("actor-1", "inc-1", new AssignIncidentRequest("agent-1"));

        assertThat(incident.getAssignedToId()).isEqualTo("agent-1");
        assertThat(incident.getStatusId()).isEqualTo("status-pending");
        verify(incidentRepository).save(any(Incident.class));
        verify(activityLogService).logIncidentAssignment("actor-1", "inc-1", "agent-1");
    }

    @Test
    void assignIncident_pendingStatusNotConfigured_throws500() {
        Incident incident = buildIncident();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findByNameIgnoreCase("Pending")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                incidentService.assignIncident("actor-1", "inc-1", new AssignIncidentRequest("agent-1")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Default 'Pending' status not configured")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(500);
    }

    @Test
    void assignIncident_incidentNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                incidentService.assignIncident("actor-1", "missing", new AssignIncidentRequest("agent-1")))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }
}
