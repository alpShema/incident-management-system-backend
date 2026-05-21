package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.*;
import com.amalitech.hilfe.repositories.*;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock IncidentRepository incidentRepository;
    @Mock IncidentTypeRepository incidentTypeRepository;
    @Mock LocationRepository locationRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
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

    private IncidentType buildIncidentType() {
        return IncidentType.builder()
                .id("type-1")
                .name("Topic")
                .categoryId("cat-1")
                .build();
    }

    // ── createIncident ────────────────────────────────────────────────────────

    @Test
    void createIncident_happyPath_returnsIncidentResponse() {
        Incident incident = buildIncident();
        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
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
        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
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
        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
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
        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
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

        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
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
        when(incidentTypeRepository.findById("bad-type")).thenReturn(Optional.empty());
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
        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
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
        when(incidentTypeRepository.findById("bad-type")).thenReturn(Optional.empty());
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
    void listIncidents_returnsIncidentsCreatedByUser() {
        Incident incident = buildIncident();
        Page<Incident> page = new PageImpl<>(List.of(incident));
        when(incidentRepository.findByUserIdFiltered(anyString(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.listIncidents(
                "user-1", RoleCode.CLIENT, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        verify(incidentRepository).findByUserIdFiltered(eq("user-1"), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void listIncidents_withFilters_passesFiltersToRepository() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findByUserIdFiltered(anyString(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        incidentService.listIncidents(
                "user-1", RoleCode.CLIENT, "status-open", "sev-high", "type-fire", "cat-facility", null, PageRequest.of(0, 20));

        verify(incidentRepository).findByUserIdFiltered(
                eq("user-1"), eq("status-open"), eq("sev-high"), eq("type-fire"), eq("cat-facility"), any(), any(Pageable.class));
    }

    // ── searchIncidents ───────────────────────────────────────────────────────

    @Test
    void searchIncidents_clientRole_delegatesToSearchByUserId() {
        Incident incident = buildIncident();
        Page<Incident> page = new PageImpl<>(List.of(incident));
        when(incidentRepository.searchByUserId(eq("user-1"), eq("%fire%"), any(Pageable.class))).thenReturn(page);

        Page<IncidentResponse> result = incidentService.searchIncidents("user-1", RoleCode.CLIENT, "fire", Pageable.unpaged());

        assertThat(result).hasSize(1);
        verify(incidentRepository).searchByUserId(eq("user-1"), eq("%fire%"), any(Pageable.class));
    }

    @Test
    void searchIncidents_agentRole_delegatesToSearchByDepartment() {
        Incident incident = buildIncident();
        Page<Incident> page = new PageImpl<>(List.of(incident));
        Agent agent = Agent.builder().id("agent-row-1").userId("agent-user-1").build();
        when(agentRepository.findByUserId("agent-user-1")).thenReturn(Optional.of(agent));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-row-1")).thenReturn(List.of("dept-1"));
        when(incidentRepository.searchByDepartment(eq(List.of("dept-1")), eq("%projector%"), any(Pageable.class)))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.searchIncidents("agent-user-1", RoleCode.AGENT, "projector", Pageable.unpaged());

        assertThat(result).hasSize(1);
        verify(incidentRepository).searchByDepartment(eq(List.of("dept-1")), eq("%projector%"), any(Pageable.class));
    }

    @Test
    void searchIncidents_adminRole_delegatesToSearchAll() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.searchAll(eq("%network%"), any(Pageable.class))).thenReturn(page);

        Page<IncidentResponse> result = incidentService.searchIncidents("admin-1", RoleCode.ADMIN, "network", Pageable.unpaged());

        assertThat(result).isEmpty();
        verify(incidentRepository).searchAll(eq("%network%"), any(Pageable.class));
    }

    @Test
    void searchIncidents_superAdminRole_delegatesToSearchAll() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.searchAll(eq("%access%"), any(Pageable.class))).thenReturn(page);

        incidentService.searchIncidents("super-1", RoleCode.SUPER_ADMIN, "access", Pageable.unpaged());

        verify(incidentRepository).searchAll(eq("%access%"), any(Pageable.class));
    }

    @Test
    void searchIncidents_blankQuery_throws400() {
        assertThatThrownBy(() -> incidentService.searchIncidents("user-1", RoleCode.CLIENT, "  ", Pageable.unpaged()))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Search query must not be blank")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void searchIncidents_nullQuery_throws400() {
        assertThatThrownBy(() -> incidentService.searchIncidents("user-1", RoleCode.CLIENT, null, Pageable.unpaged()))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Search query must not be blank")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void searchIncidents_agentWithoutAgentRecord_returnsEmptyPage() {
        when(agentRepository.findByUserId("agent-user-1")).thenReturn(Optional.empty());

        Page<IncidentResponse> result = incidentService.searchIncidents("agent-user-1", RoleCode.AGENT, "incident", Pageable.unpaged());

        assertThat(result).isEmpty();
    }

    @Test
    void searchIncidents_queryWithPercentSign_escapesWildcard() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.searchByUserId(eq("user-1"), eq("%fire\\%%"), any(Pageable.class))).thenReturn(page);

        incidentService.searchIncidents("user-1", RoleCode.CLIENT, "fire%", Pageable.unpaged());

        verify(incidentRepository).searchByUserId(eq("user-1"), eq("%fire\\%%"), any(Pageable.class));
    }

    @Test
    void searchIncidents_queryWithUnderscore_escapesWildcard() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.searchByUserId(eq("user-1"), eq("%fire\\_test%"), any(Pageable.class))).thenReturn(page);

        incidentService.searchIncidents("user-1", RoleCode.CLIENT, "fire_test", Pageable.unpaged());

        verify(incidentRepository).searchByUserId(eq("user-1"), eq("%fire\\_test%"), any(Pageable.class));
    }

    // ── getIncident ───────────────────────────────────────────────────────────

    @Test
    void getIncident_creator_returnsResponse() {
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

        IncidentResponse response = incidentService.getIncident("user-1", RoleCode.CLIENT, "inc-1");

        assertThat(response.id()).isEqualTo("inc-1");
        assertThat(response.attachments()).containsExactly(mediaResponse);
    }

    @Test
    void getIncident_admin_canAccessAnyIncident() {
        Incident incident = buildIncident();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(mediaRepository.findByIncidentId("inc-1")).thenReturn(List.of());
        when(mediaService.toMediaResponses(List.of())).thenReturn(List.of());

        IncidentResponse response = incidentService.getIncident("other-admin", RoleCode.ADMIN, "inc-1");

        assertThat(response.id()).isEqualTo("inc-1");
    }

    @Test
    void getIncident_assignedAgent_canAccess() {
        Incident incident = buildIncident();
        incident.setAssignedToId("agent-row-1");
        Agent agent = Agent.builder().id("agent-row-1").userId("agent-user-1").build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("agent-user-1")).thenReturn(Optional.of(agent));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-row-1")).thenReturn(List.of("dept-1"));
        when(mediaRepository.findByIncidentId("inc-1")).thenReturn(List.of());
        when(mediaService.toMediaResponses(List.of())).thenReturn(List.of());

        IncidentResponse response = incidentService.getIncident("agent-user-1", RoleCode.AGENT, "inc-1");

        assertThat(response.id()).isEqualTo("inc-1");
    }

    @Test
    void getIncident_unrelatedUser_throws403() {
        Incident incident = buildIncident();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.getIncident("other-user", RoleCode.CLIENT, "inc-1"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void getIncident_unassignedAgent_throws403() {
        Incident incident = buildIncident();
        incident.setAssignedToId("different-agent");
        Agent agent = Agent.builder().id("agent-row-1").userId("agent-user-1").build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("agent-user-1")).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> incidentService.getIncident("agent-user-1", RoleCode.AGENT, "inc-1"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void getIncident_notFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncident("user-1", RoleCode.CLIENT, "missing"))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_agent_inProgressToPending_savesAndLogs() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        Incident incident = buildIncident();
        incident.setStatus(inProgressStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-pending"));

        verify(incidentRepository).save(any(Incident.class));
        verify(activityLogService).logIncidentStatusChange("actor-1", "inc-1", "In Progress", "Pending");
    }

    @Test
    void updateStatus_agent_inProgressToResolved_savesAndLogs() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");

        Incident incident = buildIncident();
        incident.setStatus(inProgressStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-resolved")).thenReturn(Optional.of(resolvedStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-resolved"));

        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void updateStatus_agent_pendingToInProgress_savesAndLogs() {
        Status pendingStatus    = buildStatus("status-pending",     "Pending");
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Incident incident = buildIncident();
        incident.setStatus(pendingStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-in-progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-in-progress"));

        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void updateStatus_client_resolvedToClosed_savesAndLogs() {
        Status resolvedStatus = buildStatus("status-resolved", "Resolved");
        Status closedStatus   = buildStatus("status-closed",   "Closed");

        Incident incident = buildIncident();
        incident.setStatus(resolvedStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-closed")).thenReturn(Optional.of(closedStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-closed"));

        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void updateStatus_client_resolvedToReopened_agentActive_keepAgentAndTransitionsToInProgress() {
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");
        Status reopenedStatus   = buildStatus("status-reopened",    "Reopened");
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Agent agent = Agent.builder().id("agent-1").userId("user-agent-1").status(true).build();

        Incident incident = buildIncident();
        incident.setStatus(resolvedStatus);
        incident.setAssignedToId("agent-1");

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-reopened")).thenReturn(Optional.of(reopenedStatus));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-reopened"));

        assertThat(incident.getStatusId()).isEqualTo("status-in-progress");
        assertThat(incident.getAssignedToId()).isEqualTo("agent-1");
        verify(activityLogService).logIncidentStatusChange("actor-1", "inc-1", "Resolved", "Reopened");
        verify(activityLogService).logIncidentStatusChange("actor-1", "inc-1", "Reopened", "In Progress");
    }

    @Test
    void updateStatus_client_resolvedToReopened_agentUnavailable_clearsAgentAndTransitionsToInProgress() {
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");
        Status reopenedStatus   = buildStatus("status-reopened",    "Reopened");
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Agent inactiveAgent = Agent.builder().id("agent-1").userId("user-agent-1").status(false).build();

        Incident incident = buildIncident();
        incident.setStatus(resolvedStatus);
        incident.setAssignedToId("agent-1");

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-reopened")).thenReturn(Optional.of(reopenedStatus));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(inactiveAgent));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-reopened"));

        assertThat(incident.getStatusId()).isEqualTo("status-in-progress");
        assertThat(incident.getAssignedToId()).isNull();
        verify(activityLogService).logIncidentStatusChange("actor-1", "inc-1", "Resolved", "Reopened");
        verify(activityLogService).logIncidentStatusChange("actor-1", "inc-1", "Reopened", "In Progress");
    }

    @Test
    void updateStatus_admin_anyToClosed_override_succeeds() {
        Status openStatus   = buildStatus("status-open",   "Open");
        Status closedStatus = buildStatus("status-closed", "Closed");

        Incident incident = buildIncident();
        incident.setStatus(openStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-closed")).thenReturn(Optional.of(closedStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.ADMIN, "inc-1", new UpdateIncidentStatusRequest("status-closed"));

        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void updateStatus_invalidTransition_throws400() {
        Status closedStatus = buildStatus("status-closed", "Closed");
        Status openStatus   = buildStatus("status-open",   "Open");

        Incident incident = buildIncident();
        incident.setStatus(closedStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-open")).thenReturn(Optional.of(openStatus));

        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-open")))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(422);
    }

    @Test
    void updateStatus_nonExistentTransitionPath_throws422() {
        Status closedStatus = buildStatus("status-closed", "Closed");
        Status openStatus   = buildStatus("status-open",   "Open");

        Incident incident = buildIncident();
        incident.setStatus(closedStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-open")).thenReturn(Optional.of(openStatus));

        // closed → open is not a defined path at all
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-open")))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(422);
    }

    @Test
    void updateStatus_wrongRole_validPath_throws403() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");

        Incident incident = buildIncident();
        incident.setStatus(inProgressStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-resolved")).thenReturn(Optional.of(resolvedStatus));

        // in-progress → resolved is a valid path but only for AGENT, not CLIENT
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-resolved")))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void updateStatus_nonAgent_attemptsPending_throws403() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        Incident incident = buildIncident();
        incident.setStatus(inProgressStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));

        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-pending")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You do not have permission to move an incident to 'Pending'")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void updateStatus_statusNotFound_throws404() {
        Incident incident = buildIncident();
        incident.setStatus(buildStatus("status-in-progress", "In Progress"));

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("bad-status")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("bad-status")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Status not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateStatus_incidentNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "missing", new UpdateIncidentStatusRequest("status-pending")))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateStatus_transitionToClosed_setsClosedAt() {
        Status resolvedStatus = buildStatus("status-resolved", "Resolved");
        Status closedStatus   = buildStatus("status-closed",   "Closed");

        Incident incident = buildIncident();
        incident.setStatus(resolvedStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-closed")).thenReturn(Optional.of(closedStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-closed"));

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
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.assignIncident("actor-1", "inc-1", new AssignIncidentRequest("agent-1"));

        assertThat(incident.getAssignedToId()).isEqualTo("agent-1");
        assertThat(incident.getStatusId()).isEqualTo("status-in-progress");
        verify(incidentRepository).save(any(Incident.class));
        verify(activityLogService).logIncidentAssignment("actor-1", "inc-1", "agent-1");
    }

    @Test
    void assignIncident_inProgressStatusNotConfigured_throws500() {
        Incident incident = buildIncident();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                incidentService.assignIncident("actor-1", "inc-1", new AssignIncidentRequest("agent-1")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Default 'In Progress' status not configured")
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
