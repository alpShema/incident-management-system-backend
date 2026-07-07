package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.*;
import com.amalitech.hilfe.notifications.NotificationEventPublisher;
import com.amalitech.hilfe.notifications.events.*;
import com.amalitech.hilfe.repositories.*;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.AutoCloseService;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.MediaService;
import com.amalitech.hilfe.services.SlaService;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T10:30:00Z");

    @Mock IncidentRepository incidentRepository;
    @Mock IncidentTypeRepository incidentTypeRepository;
    @Mock LocationRepository locationRepository;
    @Mock AgentGroupRepository agentGroupRepository;
    @Mock AgentGroupMemberRepository agentGroupMemberRepository;
    @Mock AgentRepository agentRepository;
    @Mock UserRepository userRepository;
    @Mock AdminRepository adminRepository;
    @Mock StatusRepository statusRepository;
    @Mock SeverityRepository severityRepository;
    @Mock ActivityLogService activityLogService;
    @Mock AutoCloseService autoCloseService;
    @Mock NotificationEventPublisher notificationEventPublisher;
    @Mock MediaService mediaService;
    @Mock MediaRepository mediaRepository;
    @Mock SlaService slaService;
    @Mock EntityManager entityManager;
    @InjectMocks IncidentService incidentService;

    @BeforeEach
    void injectEntityManager() {
        ReflectionTestUtils.setField(incidentService, "entityManager", entityManager);
        lenient().when(slaService.toIncidentResponse(any(Incident.class)))
                .thenAnswer(invocation -> IncidentResponse.from(invocation.getArgument(0), null, null));
        lenient().when(slaService.toIncidentResponse(any(Incident.class), anyList()))
                .thenAnswer(invocation -> IncidentResponse.from(invocation.getArgument(0), invocation.getArgument(1), null));
        lenient().when(slaService.toIncidentResponsePage(any()))
                .thenAnswer(invocation -> {
                    Page<Incident> page = invocation.getArgument(0);
                    List<IncidentResponse> content = page.getContent().stream()
                            .map(incident -> IncidentResponse.from(incident, null, null))
                            .toList();
                    return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
                });
        lenient().when(agentRepository.hasActiveGroup(any(), any())).thenReturn(true);
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

    /** Builds an incident assigned to actor-1 (agentId=agent-1, userId=actor-1). */
    private Incident buildAssignedIncident() {
        Incident incident = buildIncident();
        incident.setAssignedToId("agent-1");
        return incident;
    }

    /** Stubs agentRepository so agent-1 resolves to userId actor-1. */
    private void stubAssignedAgent() {
        Agent agent = Agent.builder().id("agent-1").userId("actor-1").status(true).build();
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
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
        verify(entityManager, times(2)).flush();
        verify(slaService).onIncidentCreated(incident);
    }

    @Test
    void createIncident_trimsTitleAndDescription() {
        Incident incident = buildIncident();
        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));

        CreateIncidentRequest request = new CreateIncidentRequest(
                "  Test Incident  ", "  Test description  ", "type-1", "loc-1", null, null);
        incidentService.createIncident("user-1", request);

        var incidentCaptor = forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        assertThat(incidentCaptor.getValue().getTitle()).isEqualTo("Test Incident");
        assertThat(incidentCaptor.getValue().getDescription()).isEqualTo("Test description");
    }

    @Test
    void createIncident_autoAssigned_notifiesClient() {
        Incident incident = buildAssignedIncident(); // assignedToId = "agent-1"
        Agent agent = Agent.builder().id("agent-1").userId("agent-user-1").status(true).build();

        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(userRepository.findById("agent-user-1")).thenReturn(Optional.of(
                com.amalitech.hilfe.models.User.builder().id("agent-user-1").fullName("Jane Doe").build()));

        incidentService.createIncident("user-1", new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null));

        verify(notificationEventPublisher).publish(new IncidentAutoAssignedClientEvent("user-1", incident.getId(), 1, "Jane Doe"));
        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentAssignedEvent ev
                && "agent-user-1".equals(ev.recipientUserId()) && incident.getId().equals(ev.incidentId()) && ev.incidentNo() == 1));
    }

    @Test
    void createIncident_autoAssigned_logsAutoAssignment() {
        Incident incident = buildAssignedIncident(); // assignedToId = "agent-1"
        Agent agent = Agent.builder().id("agent-1").userId("agent-user-1").status(true).build();

        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));

        incidentService.createIncident("user-1", new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null));

        verify(activityLogService).logIncidentAutoAssignment(incident.getId(), "agent-1");
    }

    @Test
    void createIncident_autoAssigned_setsStatusToInProgress() {
        Agent agent = Agent.builder().id("agent-1").userId("agent-user-1").status(true).build();
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        AgentGroup agentGroup = AgentGroup.builder().id("group-1").name("Test Group").status(true).build();
        IncidentType incidentType = IncidentType.builder()
                .id("type-1")
                .name("Topic")
                .categoryId("cat-1")
                .agentGroupId("group-1")
                .build();
        Incident savedIncident = buildIncident();
        savedIncident.setAssignedToId("agent-1");
        savedIncident.setStatusId("status-in-progress");

        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(incidentType));
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(incidentRepository.findByIdWithDetails(anyString())).thenReturn(Optional.of(savedIncident));
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(agentGroup));
        when(agentRepository.findAvailableByAgentGroupIdAndLocation("group-1", "loc-1")).thenReturn(List.of(agent));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));

        var incidentCaptor = forClass(Incident.class);
        incidentService.createIncident("user-1", new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null));

        verify(incidentRepository).save(incidentCaptor.capture());
        Incident capturedIncident = incidentCaptor.getValue();
        assertThat(capturedIncident.getStatusId()).isEqualTo("status-in-progress");
        verify(statusRepository).findByNameIgnoreCase("In Progress");
        verify(statusRepository, never()).findByNameIgnoreCase("Pending");
    }

    @Test
    void createIncident_deactivatedAgentGroup_setsStatusOpenAndNotifiesAdmins() {
        AgentGroup deactivatedGroup = AgentGroup.builder().id("group-1").name("Test Group").status(false).build();
        IncidentType incidentType = IncidentType.builder()
                .id("type-1")
                .name("Topic")
                .categoryId("cat-1")
                .agentGroupId("group-1")
                .build();
        Incident incident = buildIncident();

        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(incidentType));
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(deactivatedGroup));
        when(userRepository.findActiveAdminUserIds()).thenReturn(List.of("admin-user-1"));

        var incidentCaptor = forClass(Incident.class);
        incidentService.createIncident("user-1", new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null));

        verify(incidentRepository).save(incidentCaptor.capture());
        Incident capturedIncident = incidentCaptor.getValue();
        assertThat(capturedIncident.getAssignedToId()).isNull();
        assertThat(capturedIncident.getStatusId()).isEqualTo("status-open");
        verify(agentRepository, never()).findAvailableByAgentGroupIdAndLocation(any(), any());
        verify(agentRepository, never()).findAvailableByAgentGroupIdViaMembership(any());
        verify(notificationEventPublisher).publish(new IncidentEscalatedEvent("admin-user-1", incident.getId(), 1));
        verify(notificationEventPublisher, never()).publish(isA(IncidentAutoAssignedClientEvent.class));
    }

    @Test
    void createIncident_noAutoAssignment_doesNotLogAutoAssignment() {
        Incident incident = buildIncident(); // assignedToId = null

        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));

        incidentService.createIncident("user-1", new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null));

        verify(activityLogService, never()).logIncidentAutoAssignment(any(), any());
    }

    @Test
    void createIncident_noAutoAssignment_doesNotNotifyClient() {
        Incident incident = buildIncident(); // assignedToId = null

        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));

        incidentService.createIncident("user-1", new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null));

        verify(notificationEventPublisher, never()).publish(isA(IncidentAutoAssignedClientEvent.class));
    }

    @Test
    void createIncident_noAutoAssignment_notifiesAllActiveAdmins() {
        Incident incident = buildIncident(); // assignedToId = null

        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));
        when(userRepository.findActiveAdminUserIds()).thenReturn(List.of("admin-user-1", "admin-user-2"));

        incidentService.createIncident("user-1", new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null));

        verify(notificationEventPublisher).publish(new IncidentEscalatedEvent("admin-user-1", incident.getId(), 1));
        verify(notificationEventPublisher).publish(new IncidentEscalatedEvent("admin-user-2", incident.getId(), 1));
        verify(notificationEventPublisher, never()).publish(isA(IncidentAutoAssignedClientEvent.class));
    }

    @Test
    void createIncident_noAutoAssignment_noAdminsAtAll_doesNotThrow() {
        Incident incident = buildIncident();

        when(incidentTypeRepository.findById("type-1")).thenReturn(Optional.of(buildIncidentType()));
        when(locationRepository.existsById("loc-1")).thenReturn(true);
        when(severityRepository.findByNameIgnoreCase("Low")).thenReturn(Optional.of(buildSeverity("sev-low", "Low")));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);
        when(incidentRepository.findByIdWithDetails(incident.getId())).thenReturn(Optional.of(incident));
        when(userRepository.findActiveAdminUserIds()).thenReturn(List.of());

        incidentService.createIncident("user-1", new CreateIncidentRequest(
                "Test Incident", "Test description", "type-1", "loc-1", null, null));

        verify(notificationEventPublisher, never()).publish(isA(IncidentEscalatedEvent.class));
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

    // ── queryIncidents ────────────────────────────────────────────────────────

    @Test
    void queryIncidents_returnsIncidentsCreatedByUser() {
        Incident incident = buildIncident();
        Page<Incident> page = new PageImpl<>(List.of(incident));
        when(incidentRepository.findByUserIdUnified(anyString(), any(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class)))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.queryIncidents(
                "user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), PageRequest.of(0, 20));

        assertThat(result).isNotNull().hasSize(1);
        verify(incidentRepository).findByUserIdUnified(eq("user-1"), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class));
    }

    @Test
    void queryIncidents_withFilters_passesFiltersToRepository() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findByUserIdUnified(anyString(), any(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class)))
                .thenReturn(page);

        incidentService.queryIncidents(
                "user-1", null, new IncidentFilterParams("status-open", "sev-high", "type-fire", "cat-facility", null), new IncidentDateFilter(null, null), PageRequest.of(0, 20));

        verify(incidentRepository).findByUserIdUnified(
                eq("user-1"), isNull(), eq(new IncidentFilterParams("status-open", "sev-high", "type-fire", "cat-facility", null)), any(IncidentDateFilter.class), any(Pageable.class));
    }

    @Test
    void queryIncidents_withKeyword_buildsLikePattern() {
        Incident incident = buildIncident();
        Page<Incident> page = new PageImpl<>(List.of(incident));
        when(incidentRepository.findByUserIdUnified(anyString(), eq("%fire%"), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class)))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.queryIncidents(
                "user-1", "fire", new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), PageRequest.of(0, 20));

        assertThat(result).hasSize(1);
        verify(incidentRepository).findByUserIdUnified(eq("user-1"), eq("%fire%"), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any(Pageable.class));
    }

    // ── searchIncidents ───────────────────────────────────────────────────────

    @Test
    void searchIncidents_searchesByUserId() {
        Incident incident = buildIncident();
        Page<Incident> page = new PageImpl<>(List.of(incident));
        when(incidentRepository.searchByUserId(eq("user-1"), eq("%fire%"), any(), anyBoolean(), any(), anyBoolean(), any(Pageable.class))).thenReturn(page);

        Page<IncidentResponse> result = incidentService.searchIncidents("user-1", "fire", null, null, Pageable.unpaged());

        assertThat(result).hasSize(1);
        verify(incidentRepository).searchByUserId(eq("user-1"), eq("%fire%"), any(), anyBoolean(), any(), anyBoolean(), any(Pageable.class));
    }

    @Test
    void searchIncidents_blankQuery_throws400() {
        Pageable pageable = Pageable.unpaged();
        assertThatThrownBy(() -> incidentService.searchIncidents("user-1", "  ", null, null, pageable))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Search query must not be blank")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void searchIncidents_nullQuery_throws400() {
        Pageable pageable = Pageable.unpaged();
        assertThatThrownBy(() -> incidentService.searchIncidents("user-1", null, null, null, pageable))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Search query must not be blank")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void searchIncidents_queryWithPercentSign_escapesWildcard() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.searchByUserId(eq("user-1"), eq("%fire!%%"), any(), anyBoolean(), any(), anyBoolean(), any(Pageable.class))).thenReturn(page);

        incidentService.searchIncidents("user-1", "fire%", null, null, Pageable.unpaged());

        verify(incidentRepository).searchByUserId(eq("user-1"), eq("%fire!%%"), any(), anyBoolean(), any(), anyBoolean(), any(Pageable.class));
    }

    @Test
    void searchIncidents_queryWithUnderscore_escapesWildcard() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.searchByUserId(eq("user-1"), eq("%fire!_test%"), any(), anyBoolean(), any(), anyBoolean(), any(Pageable.class))).thenReturn(page);

        incidentService.searchIncidents("user-1", "fire_test", null, null, Pageable.unpaged());

        verify(incidentRepository).searchByUserId(eq("user-1"), eq("%fire!_test%"), any(), anyBoolean(), any(), anyBoolean(), any(Pageable.class));
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
        when(mediaRepository.findByIncidentId("inc-1")).thenReturn(List.of());
        when(mediaService.toMediaResponses(List.of())).thenReturn(List.of());

        IncidentResponse response = incidentService.getIncident("agent-user-1", RoleCode.AGENT, "inc-1");

        assertThat(response.id()).isEqualTo("inc-1");
    }

    @Test
    void getIncident_assignedAgent_noGroup_canAccess() {
        Incident incident = buildIncident();
        incident.setAssignedToId("agent-row-1");
        Agent agent = Agent.builder().id("agent-row-1").userId("agent-user-1").build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("agent-user-1")).thenReturn(Optional.of(agent));
        when(mediaRepository.findByIncidentId("inc-1")).thenReturn(List.of());
        when(mediaService.toMediaResponses(List.of())).thenReturn(List.of());

        IncidentResponse response = incidentService.getIncident("agent-user-1", RoleCode.AGENT, "inc-1");

        assertThat(response.id()).isEqualTo("inc-1");
        // assignee check short-circuits before any group lookup
        verify(agentGroupMemberRepository, never()).findAgentGroupIdsByAgentId(any());
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
    void getIncident_agentInSameDepartmentDifferentGroup_canAccess() {
        // Agent A (group-x, dept-d) views incident assigned to Agent B (group-y, dept-d).
        // The dept-incidents view shows this incident to Agent A, so the detail endpoint
        // must also allow access — comparing departments, not raw groups.
        Incident incident = buildIncident();
        incident.setAssignedToId("agent-b");
        Agent agentA = Agent.builder().id("agent-a").userId("agent-user-a").build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("agent-user-a")).thenReturn(Optional.of(agentA));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-a")).thenReturn(List.of("group-x"));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-b")).thenReturn(List.of("group-y"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("group-x"))).thenReturn(List.of("dept-d"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("group-y"))).thenReturn(List.of("dept-d"));
        when(mediaRepository.findByIncidentId("inc-1")).thenReturn(List.of());
        when(mediaService.toMediaResponses(List.of())).thenReturn(List.of());

        IncidentResponse response = incidentService.getIncident("agent-user-a", RoleCode.AGENT, "inc-1");

        assertThat(response.id()).isEqualTo("inc-1");
    }

    @Test
    void getIncident_agentInDifferentDepartment_throws403() {
        Incident incident = buildIncident();
        incident.setAssignedToId("agent-b");
        Agent agentA = Agent.builder().id("agent-a").userId("agent-user-a").build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findByUserId("agent-user-a")).thenReturn(Optional.of(agentA));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-a")).thenReturn(List.of("group-x"));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-b")).thenReturn(List.of("group-y"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("group-x"))).thenReturn(List.of("dept-1"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("group-y"))).thenReturn(List.of("dept-2"));

        assertThatThrownBy(() -> incidentService.getIncident("agent-user-a", RoleCode.AGENT, "inc-1"))
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

        Incident incident = buildAssignedIncident();
        incident.setStatus(inProgressStatus);
        stubAssignedAgent();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-pending", "Waiting for parts"));

        verify(incidentRepository).save(any(Incident.class));
        verify(activityLogService).logIncidentStatusChange("actor-1", "inc-1", "In Progress", "Pending", "Waiting for parts");
    }

    @Test
    void updateStatus_agent_inProgressToResolved_savesAndLogs() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");

        Incident incident = buildAssignedIncident();
        incident.setStatus(inProgressStatus);
        stubAssignedAgent();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-resolved")).thenReturn(Optional.of(resolvedStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-resolved", null));

        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void updateStatus_agent_pendingToInProgress_savesAndLogs() {
        Status pendingStatus    = buildStatus("status-pending",     "Pending");
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Incident incident = buildAssignedIncident();
        incident.setStatus(pendingStatus);
        stubAssignedAgent();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-in-progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-in-progress", null));

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

        incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-closed", null));

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

        incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-reopened", "Issue recurred"));

        assertThat(incident.getStatusId()).isEqualTo("status-in-progress");
        assertThat(incident.getAssignedToId()).isEqualTo("agent-1");
        verify(activityLogService).logIncidentStatusChange("actor-1", "inc-1", "Resolved", "Reopened", "Issue recurred");
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

        incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-reopened", "Issue recurred"));

        assertThat(incident.getStatusId()).isEqualTo("status-in-progress");
        assertThat(incident.getAssignedToId()).isNull();
        verify(activityLogService).logIncidentStatusChange("actor-1", "inc-1", "Resolved", "Reopened", "Issue recurred");
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

        incidentService.updateStatus("actor-1", RoleCode.ADMIN, "inc-1", new UpdateIncidentStatusRequest("status-closed", null));

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

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-open", null);
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", request))
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
        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-open", null);
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", request))
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
        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-resolved", null);
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", request))
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

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-pending", "Waiting for parts");
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You do not have permission to move an incident to 'Pending'")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    // ── creator contextual role ───────────────────────────────────────────────

    @Test
    void updateStatus_creatorAgent_toPending_throws403() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        Incident incident = buildIncident();
        incident.setUserId("actor-1"); // creator
        incident.setStatus(inProgressStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-pending", "reason");
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You do not have permission to move an incident to 'Pending'")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void updateStatus_creatorAgent_toResolved_throws403() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");

        Incident incident = buildIncident();
        incident.setUserId("actor-1"); // creator
        incident.setStatus(inProgressStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-resolved")).thenReturn(Optional.of(resolvedStatus));

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-resolved", null);
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("You do not have permission to move an incident to 'Resolved'")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void updateStatus_creatorAgent_toClosed_returns200() {
        Status resolvedStatus = buildStatus("status-resolved", "Resolved");
        Status closedStatus   = buildStatus("status-closed",   "Closed");

        Incident incident = buildIncident();
        incident.setUserId("actor-1"); // creator
        incident.setStatus(resolvedStatus);
        incident.setResolvedAt(FIXED_NOW.minusSeconds(3600)); // resolved 1 hour ago

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-closed")).thenReturn(Optional.of(closedStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-closed", null));

        assertThat(incident.getClosedAt()).isNotNull();
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void updateStatus_creatorAgent_toReopened_withinWindow_returns200() {
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");
        Status reopenedStatus   = buildStatus("status-reopened",    "Reopened");
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Incident incident = buildIncident();
        incident.setUserId("actor-1"); // creator
        incident.setStatus(resolvedStatus);
        incident.setResolvedAt(FIXED_NOW.minusSeconds(3600)); // resolved 1 hour ago

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-reopened")).thenReturn(Optional.of(reopenedStatus));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(autoCloseService.readDurationSeconds()).thenReturn(Integer.MAX_VALUE); // window = 72h, resolved 1h ago → within window
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-reopened", "Issue recurred"));

        verify(incidentRepository, atLeastOnce()).save(any(Incident.class));
    }

    @Test
    void updateStatus_assignedNonCreatorAgent_toPending_returns200() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        // userId="user-1" (creator), assignedToId="agent-1" resolved to actorUserId="actor-1"
        Incident incident = buildAssignedIncident();
        incident.setStatus(inProgressStatus);
        stubAssignedAgent();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-pending", "Waiting for parts"));

        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void updateStatus_assignedNonCreatorAgent_toResolved_returns200() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");

        Incident incident = buildAssignedIncident();
        incident.setStatus(inProgressStatus);
        stubAssignedAgent();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-resolved")).thenReturn(Optional.of(resolvedStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-resolved", null));

        assertThat(incident.getResolvedAt()).isNotNull();
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void updateStatus_unassignedAgent_toPending_throws403() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        // Incident assigned to a different agent (agent-1 → userId "other-user"), actor is "actor-1"
        Incident incident = buildAssignedIncident();
        incident.setStatus(inProgressStatus);
        Agent otherAgent = Agent.builder().id("agent-1").userId("other-user").status(true).build();
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(otherAgent));

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-pending", "reason");
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("You are not the assigned agent for this incident")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void updateStatus_unassignedAgent_statusUnchangedAfterRejection() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        Incident incident = buildAssignedIncident();
        incident.setStatus(inProgressStatus);
        Agent otherAgent = Agent.builder().id("agent-1").userId("other-user").status(true).build();
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(otherAgent));

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-pending", "reason");
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", request))
                .isInstanceOf(ArmsAuthException.class);

        assertThat(incident.getStatusId()).isEqualTo("status-open"); // unchanged
        verify(incidentRepository, never()).save(any(Incident.class));
    }

    @Test
    void updateStatus_statusNotFound_throws404() {
        Incident incident = buildIncident();
        incident.setStatus(buildStatus("status-in-progress", "In Progress"));

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("bad-status")).thenReturn(Optional.empty());

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("bad-status", null);
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Status not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateStatus_incidentNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-pending", "Waiting for parts");
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "missing", request))
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

        incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", new UpdateIncidentStatusRequest("status-closed", null));

        assertThat(incident.getClosedAt()).isNotNull();
        verify(incidentRepository).save(any(Incident.class));
    }

    // ── reason enforcement ────────────────────────────────────────────────────

    @Test
    void updateStatus_pendingWithoutReason_throws400() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        Incident incident = buildAssignedIncident();
        incident.setStatus(inProgressStatus);
        stubAssignedAgent();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-pending", null);
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("A reason is required when setting status to Pending")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void updateStatus_reopenedWithoutReason_throws400() {
        Status resolvedStatus = buildStatus("status-resolved", "Resolved");
        Status reopenedStatus = buildStatus("status-reopened", "Reopened");

        Incident incident = buildIncident();
        incident.setStatus(resolvedStatus);
        incident.setResolvedAt(FIXED_NOW.minus(1, java.time.temporal.ChronoUnit.HOURS));

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-reopened")).thenReturn(Optional.of(reopenedStatus));
        when(autoCloseService.readDurationSeconds()).thenReturn(Integer.MAX_VALUE);

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest("status-reopened", "");
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.CLIENT, "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("A reason is required when setting status to Reopened")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void updateStatus_pendingWithReason_savesReason() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        Incident incident = buildAssignedIncident();
        incident.setStatus(inProgressStatus);
        stubAssignedAgent();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", new UpdateIncidentStatusRequest("status-pending", "Waiting for parts"));

        assertThat(incident.getStatusReason()).isEqualTo("Waiting for parts");
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
    void updateSeverity_notifyClient_whenActorIsNotCreator() {
        Incident incident = buildIncident(); // userId = "user-1", actor = "actor-1"
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateSeverity("actor-1", "inc-1", new UpdateIncidentSeverityRequest("sev-high"));

        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentSeverityChangedEvent ev
                && "user-1".equals(ev.recipientUserId()) && ev.incidentNo() == 1
                && "none".equals(ev.previousSeverity()) && "sev-high".equals(ev.newSeverity())));
    }

    @Test
    void updateSeverity_doesNotNotifyClient_whenActorIsCreator() {
        Incident incident = buildIncident();
        incident.setUserId("actor-1"); // actor IS the creator
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateSeverity("actor-1", "inc-1", new UpdateIncidentSeverityRequest("sev-high"));

        verify(notificationEventPublisher, never()).publish(isA(IncidentSeverityChangedEvent.class));
    }

    @Test
    void updateSeverity_notifiesAssignedAgent_whenActorIsNotAgent() {
        Incident incident = buildAssignedIncident(); // assignedToId = "agent-1"
        stubAssignedAgent(); // agent-1 → userId "actor-1"... use a different agent user
        Agent agent = Agent.builder().id("agent-1").userId("agent-user-1").status(true).build();
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateSeverity("admin-actor", "inc-1", new UpdateIncidentSeverityRequest("sev-high"));

        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentSeverityChangedEvent ev
                && "agent-user-1".equals(ev.recipientUserId()) && "none".equals(ev.previousSeverity()) && "sev-high".equals(ev.newSeverity())));
    }

    @Test
    void updateSeverity_doesNotNotifyAgent_whenActorIsAgent() {
        Incident incident = buildAssignedIncident(); // assignedToId = "agent-1"
        Agent agent = Agent.builder().id("agent-1").userId("actor-1").status(true).build();
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        // actor IS the assigned agent — no self-notification
        incidentService.updateSeverity("actor-1", "inc-1", new UpdateIncidentSeverityRequest("sev-high"));

        verify(notificationEventPublisher, never()).publish(argThat(e -> e instanceof IncidentSeverityChangedEvent ev
                && "actor-1".equals(ev.recipientUserId())));
    }

    @Test
    void updateSeverity_unassignedIncident_doesNotNotifyAgent() {
        Incident incident = buildIncident(); // no assignedToId
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateSeverity("actor-1", "inc-1", new UpdateIncidentSeverityRequest("sev-high"));

        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentSeverityChangedEvent ev
                && "user-1".equals(ev.recipientUserId()) && "none".equals(ev.previousSeverity()) && "sev-high".equals(ev.newSeverity())));
        verifyNoMoreInteractions(notificationEventPublisher);
    }

    @Test
    void updateSeverity_incidentNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        UpdateIncidentSeverityRequest request = new UpdateIncidentSeverityRequest("sev-high");
        assertThatThrownBy(() ->
                incidentService.updateSeverity("actor-1", "missing", request))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // ── assignIncident ────────────────────────────────────────────────────────

    @Test
    void assignIncident_notifiesClient_onAssignment() {
        Incident incident = buildIncident(); // userId = "user-1"
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Agent agent = Agent.builder().id("agent-1").userId("actor-1").status(true).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.assignIncident("actor-1", "ADMIN", "inc-1", new AssignIncidentRequest("agent-1"));

        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentClientReassignedEvent ev
                && "user-1".equals(ev.recipientUserId()) && ev.incidentNo() == 1));
    }

    @Test
    void assignIncident_happyPath_setsAgentAndStatusAndLogs() {
        Incident incident = buildIncident();
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Agent agent = Agent.builder().id("agent-1").userId("actor-1").status(true).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.assignIncident("actor-1", "ADMIN", "inc-1", new AssignIncidentRequest("agent-1"));

        assertThat(incident.getAssignedToId()).isEqualTo("agent-1");
        assertThat(incident.getStatusId()).isEqualTo("status-in-progress");
        verify(incidentRepository).save(any(Incident.class));
        verify(activityLogService).logIncidentAssignment("actor-1", "inc-1", "agent-1");
    }

    @Test
    void assignIncident_inProgressStatusNotConfigured_throws500() {
        Incident incident = buildIncident();
        Agent agent = Agent.builder().id("agent-1").userId("actor-1").status(true).build();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.empty());

        AssignIncidentRequest request = new AssignIncidentRequest("agent-1");
        assertThatThrownBy(() ->
                incidentService.assignIncident("actor-1", "ADMIN", "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Default 'In Progress' status not configured")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(500);
    }

    @Test
    void assignIncident_incidentNotFound_throws404() {
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());

        AssignIncidentRequest request = new AssignIncidentRequest("agent-1");
        assertThatThrownBy(() ->
                incidentService.assignIncident("actor-1", "ADMIN", "missing", request))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void assignIncident_agentNotFound_throws404() {
        Incident incident = buildIncident();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-999")).thenReturn(Optional.empty());

        AssignIncidentRequest request = new AssignIncidentRequest("agent-999");
        assertThatThrownBy(() ->
                incidentService.assignIncident("actor-1", "ADMIN", "inc-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void assignIncident_unavailableAgent_throws400() {
        Incident incident = buildIncident();
        Agent unavailableAgent = Agent.builder().id("agent-1").userId("actor-1").status(false).build();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(unavailableAgent));

        AssignIncidentRequest unavailableRequest = new AssignIncidentRequest("agent-1");
        assertThatThrownBy(() ->
                incidentService.assignIncident("actor-1", "ADMIN", "inc-1", unavailableRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Cannot assign incident to an unavailable agent")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void assignIncident_agentInDeactivatedGroup_throws400() {
        Incident incident = buildIncident();
        Agent agent = Agent.builder().id("agent-1").userId("actor-1").status(true).agentGroupId("group-1").build();
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(agentRepository.hasActiveGroup("group-1", "agent-1")).thenReturn(false);

        AssignIncidentRequest deactivatedGroupRequest = new AssignIncidentRequest("agent-1");
        assertThatThrownBy(() ->
                incidentService.assignIncident("actor-1", "ADMIN", "inc-1", deactivatedGroupRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Cannot assign incident to an agent in a deactivated group")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }

    @Test
    void assignIncident_agentNotOwner_throws403() {
        Incident incident = buildAssignedIncident(); // assignedToId = "agent-1", agent userId = "actor-1"
        stubAssignedAgent(); // agent-1 → userId "actor-1"
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));

        AssignIncidentRequest reassignRequest = new AssignIncidentRequest("agent-2");
        assertThatThrownBy(() ->
                incidentService.assignIncident("other-agent-user", "AGENT", "inc-1", reassignRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("You can only reassign incidents that are assigned to you")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }

    @Test
    void assignIncident_agentIsOwner_succeeds() {
        Incident incident = buildAssignedIncident(); // assignedToId = "agent-1"
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Agent agent1 = Agent.builder().id("agent-1").userId("actor-1").status(true).build();
        Agent agent2 = Agent.builder().id("agent-2").userId("other-user").status(true).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent1));
        when(agentRepository.findById("agent-2")).thenReturn(Optional.of(agent2));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        // actor-1 is the userId of agent-1, who is the assigned agent — should be allowed
        incidentService.assignIncident("actor-1", "AGENT", "inc-1", new AssignIncidentRequest("agent-2"));

        assertThat(incident.getAssignedToId()).isEqualTo("agent-2");
    }

    // ── notification: sendReopenedNotification ────────────────────────────────

    @Test
    void applyReopenTransition_sendsReopenedNotificationToAssignedAgent() {
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");
        Status reopenedStatus   = buildStatus("status-reopened",    "Reopened");
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Incident incident = buildAssignedIncident(); // assignedToId="agent-1"
        incident.setStatus(resolvedStatus);
        incident.setResolvedAt(FIXED_NOW.minusSeconds(3600));
        stubAssignedAgent(); // agent-1 → userId "actor-1"

        // The client (user-1) is the creator and triggers the reopen
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-reopened")).thenReturn(Optional.of(reopenedStatus));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(autoCloseService.readDurationSeconds()).thenReturn(Integer.MAX_VALUE);
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("user-1", RoleCode.CLIENT, "inc-1",
                new UpdateIncidentStatusRequest("status-reopened", "Issue recurred"));

        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentReopenedEvent ev
                && "actor-1".equals(ev.recipientUserId()) && "inc-1".equals(ev.incidentId())));
    }

    @Test
    void applyReopenTransition_agentInactive_sendsReopenedNotificationWithNullUserId() {
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");
        Status reopenedStatus   = buildStatus("status-reopened",    "Reopened");
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Agent inactiveAgent = Agent.builder().id("agent-1").userId("actor-1").status(false).build();

        Incident incident = buildAssignedIncident(); // assignedToId="agent-1"
        incident.setStatus(resolvedStatus);
        incident.setResolvedAt(FIXED_NOW.minusSeconds(3600));

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(inactiveAgent));
        when(statusRepository.findById("status-reopened")).thenReturn(Optional.of(reopenedStatus));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(autoCloseService.readDurationSeconds()).thenReturn(Integer.MAX_VALUE);
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("user-1", RoleCode.CLIENT, "inc-1",
                new UpdateIncidentStatusRequest("status-reopened", "Issue recurred"));

        // After inactive agent is cleared, resolveAgentUserId(null) returns null
        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentReopenedEvent ev
                && ev.recipientUserId() == null && "inc-1".equals(ev.incidentId())));
    }

    @Test
    void applyReopenTransition_agentInactive_logsUnassignment() {
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");
        Status reopenedStatus   = buildStatus("status-reopened",    "Reopened");
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Agent inactiveAgent = Agent.builder().id("agent-1").userId("actor-1").status(false).build();

        Incident incident = buildAssignedIncident(); // assignedToId="agent-1"
        incident.setStatus(resolvedStatus);
        incident.setResolvedAt(FIXED_NOW.minusSeconds(3600));

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(inactiveAgent));
        when(statusRepository.findById("status-reopened")).thenReturn(Optional.of(reopenedStatus));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(autoCloseService.readDurationSeconds()).thenReturn(Integer.MAX_VALUE);
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("user-1", RoleCode.CLIENT, "inc-1",
                new UpdateIncidentStatusRequest("status-reopened", "Issue recurred"));

        verify(activityLogService).logIncidentUnassignment("user-1", "inc-1", "agent-1");
    }

    @Test
    void applyReopenTransition_agentActive_doesNotLogUnassignment() {
        Status resolvedStatus   = buildStatus("status-resolved",    "Resolved");
        Status reopenedStatus   = buildStatus("status-reopened",    "Reopened");
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Incident incident = buildAssignedIncident(); // assignedToId="agent-1"
        incident.setStatus(resolvedStatus);
        incident.setResolvedAt(FIXED_NOW.minusSeconds(3600));
        stubAssignedAgent(); // agent-1 is active

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-reopened")).thenReturn(Optional.of(reopenedStatus));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(autoCloseService.readDurationSeconds()).thenReturn(Integer.MAX_VALUE);
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("user-1", RoleCode.CLIENT, "inc-1",
                new UpdateIncidentStatusRequest("status-reopened", "Issue recurred"));

        verify(activityLogService, never()).logIncidentUnassignment(any(), any(), any());
    }

    // ── notification: sendPendingNotification ─────────────────────────────────

    @Test
    void dispatchStatusNotifications_newStatusPending_sendsPendingNotificationToClient() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        // incident.userId="user-1" (client/creator), assignedToId="agent-1" → userId "actor-1" (the acting agent)
        Incident incident = buildAssignedIncident();
        incident.setStatus(inProgressStatus);
        stubAssignedAgent();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1",
                new UpdateIncidentStatusRequest("status-pending", "Waiting for parts"));

        // creator ("user-1") is not the actor ("actor-1") → should receive a PENDING notification
        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentPendingEvent ev
                && "user-1".equals(ev.recipientUserId()) && "Waiting for parts".equals(ev.reason())));
        verify(notificationEventPublisher, never()).publish(isA(IncidentStatusChangedEvent.class));
    }

    @Test
    void dispatchStatusNotifications_actorIsCreator_doesNotNotifyCreatorForPending() {
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Status pendingStatus    = buildStatus("status-pending",     "Pending");

        // Build an incident where the assigned agent is also the creator
        Incident incident = buildAssignedIncident();
        incident.setUserId("actor-1"); // actor is also the creator
        incident.setStatus(inProgressStatus);

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(statusRepository.findById("status-pending")).thenReturn(Optional.of(pendingStatus));

        // This transition (creator-as-agent → Pending) is blocked by the business rule,
        // so we only verify notification is NOT sent when actor equals clientUserId
        UpdateIncidentStatusRequest pendingRequest = new UpdateIncidentStatusRequest("status-pending", "reason");
        assertThatThrownBy(() ->
                incidentService.updateStatus("actor-1", RoleCode.AGENT, "inc-1", pendingRequest))
                .isInstanceOf(ArmsAuthException.class);

        verify(notificationEventPublisher, never()).publish(isA(IncidentPendingEvent.class));
    }

    // ── notification: sendUnassignedNotification ──────────────────────────────

    @Test
    void assignIncident_withPreviousAgent_sendsUnassignedNotificationBeforeOverwrite() {
        // Incident already assigned to agent-1 (userId "actor-1")
        Incident incident = buildAssignedIncident(); // assignedToId = "agent-1"
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        // agent-1 is the previous agent (userId "actor-1")
        Agent previousAgent = Agent.builder().id("agent-1").userId("actor-1").status(true).build();
        // new agent being assigned
        Agent newAgent = Agent.builder().id("agent-2").userId("new-agent-user").status(true).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(previousAgent));
        when(agentRepository.findById("agent-2")).thenReturn(Optional.of(newAgent));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.assignIncident("admin-user", "ADMIN", "inc-1", new AssignIncidentRequest("agent-2"));

        // Previous agent must receive unassigned notification
        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentUnassignedEvent ev
                && "actor-1".equals(ev.recipientUserId()) && ev.incidentNo() == 1));
        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentAssignedEvent ev
                && "new-agent-user".equals(ev.recipientUserId()) && ev.incidentNo() == 1));
    }

    @Test
    void assignIncident_noPreviousAgent_doesNotSendUnassignedNotification() {
        Incident incident = buildIncident(); // no assignedToId
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Agent newAgent = Agent.builder().id("agent-1").userId("actor-1").status(true).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(newAgent));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.assignIncident("admin-user", "ADMIN", "inc-1", new AssignIncidentRequest("agent-1"));

        verify(notificationEventPublisher, never()).publish(isA(IncidentUnassignedEvent.class));
        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentAssignedEvent ev
                && "actor-1".equals(ev.recipientUserId()) && ev.incidentNo() == 1));
    }

    @Test
    void assignIncident_sameAgentReassigned_doesNotSendUnassignedNotification() {
        // Reassigning to the same agent: unassigned notification should be suppressed
        Incident incident = buildAssignedIncident(); // assignedToId = "agent-1"
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");

        Agent sameAgent = Agent.builder().id("agent-1").userId("actor-1").status(true).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        // findById("agent-1") is called twice: once to resolve previousAgentUserId, once for the new agentUserId
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(sameAgent));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.assignIncident("admin-user", "ADMIN", "inc-1", new AssignIncidentRequest("agent-1"));

        verify(notificationEventPublisher, never()).publish(isA(IncidentUnassignedEvent.class));
        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentAssignedEvent ev
                && "actor-1".equals(ev.recipientUserId()) && ev.incidentNo() == 1));
    }

    // ── notification: sendAssignmentNotification (explicit coverage) ──────────

    @Test
    void assignIncident_happyPath_sendsAssignmentNotificationToNewAgent() {
        Incident incident = buildIncident(); // no prior assignment
        Status inProgressStatus = buildStatus("status-in-progress", "In Progress");
        Agent agent = Agent.builder().id("agent-1").userId("actor-1").status(true).build();

        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(statusRepository.findByNameIgnoreCase("In Progress")).thenReturn(Optional.of(inProgressStatus));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.assignIncident("admin-user", "ADMIN", "inc-1", new AssignIncidentRequest("agent-1"));

        verify(notificationEventPublisher).publish(argThat(e -> e instanceof IncidentAssignedEvent ev
                && "actor-1".equals(ev.recipientUserId()) && ev.incidentNo() == 1));
    }

    // ── queryAllIncidents ─────────────────────────────────────────────────────

    @Test
    void queryAllIncidents_returnsPageFromRepository() {
        Page<Incident> page = new PageImpl<>(List.of(buildIncident()));
        when(incidentRepository.findAllUnified(isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.queryAllIncidents(null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), Pageable.unpaged());

        assertThat(result).isNotNull();
        verify(incidentRepository).findAllUnified(isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any());
    }

    @Test
    void queryAllIncidents_withQuery_buildsLikePattern() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findAllUnified(anyString(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        incidentService.queryAllIncidents("fire", new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), Pageable.unpaged());

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(incidentRepository).findAllUnified(captor.capture(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any());
        assertThat(captor.getValue()).startsWith("%").endsWith("%").contains("fire");
    }

    // ── queryDeptIncidents ────────────────────────────────────────────────────

    @Test
    void queryDeptIncidents_expandsToDepartmentScope() {
        Agent agent = Agent.builder().id("agent-1").userId("user-1").build();
        Page<Incident> page = new PageImpl<>(List.of(buildIncident()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-1")).thenReturn(List.of("group-A"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("group-A"))).thenReturn(List.of("dept-IT"));
        when(agentGroupRepository.findIdsByDepartmentIds(List.of("dept-IT"))).thenReturn(List.of("group-A", "group-B", "group-C"));
        when(incidentRepository.findByDepartmentUnified(eq(List.of("group-A", "group-B", "group-C")), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.queryDeptIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(incidentRepository).findByDepartmentUnified(eq(List.of("group-A", "group-B", "group-C")), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any());
    }

    @Test
    void queryDeptIncidents_multipleDeptsUnion() {
        Agent agent = Agent.builder().id("agent-1").userId("user-1").build();
        Page<Incident> page = new PageImpl<>(List.of(buildIncident()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-1")).thenReturn(List.of("group-A", "group-X"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("group-A", "group-X"))).thenReturn(List.of("dept-IT", "dept-HR"));
        when(agentGroupRepository.findIdsByDepartmentIds(List.of("dept-IT", "dept-HR"))).thenReturn(List.of("group-A", "group-B", "group-X", "group-Y"));
        when(incidentRepository.findByDepartmentUnified(eq(List.of("group-A", "group-B", "group-X", "group-Y")), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.queryDeptIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(agentGroupRepository).findDepartmentIdsByGroupIds(List.of("group-A", "group-X"));
        verify(agentGroupRepository).findIdsByDepartmentIds(List.of("dept-IT", "dept-HR"));
    }

    @Test
    void queryDeptIncidents_groupWithNoDept_fallsBackToGroupLevel() {
        Agent agent = Agent.builder().id("agent-1").userId("user-1").build();
        Page<Incident> page = new PageImpl<>(List.of(buildIncident()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-1")).thenReturn(List.of("group-orphan"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("group-orphan"))).thenReturn(List.of());
        when(incidentRepository.findByDepartmentUnified(eq(List.of("group-orphan")), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.queryDeptIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(agentGroupRepository, never()).findIdsByDepartmentIds(any());
        verify(incidentRepository).findByDepartmentUnified(eq(List.of("group-orphan")), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any());
    }

    @Test
    void queryDeptIncidents_noAgentRecord_returnsEmptyPage() {
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        Page<IncidentResponse> result = incidentService.queryDeptIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), Pageable.unpaged());

        assertThat(result.getTotalElements()).isZero();
        verify(incidentRepository, never()).findByDepartmentUnified(any(), any(), any(), any(), any());
    }

    @Test
    void queryDeptIncidents_agentWithNoGroups_returnsEmptyPage() {
        Agent agent = Agent.builder().id("agent-1").userId("user-1").build();
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-1")).thenReturn(List.of());

        Page<IncidentResponse> result = incidentService.queryDeptIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), Pageable.unpaged());

        assertThat(result.getTotalElements()).isZero();
        verify(incidentRepository, never()).findByDepartmentUnified(any(), any(), any(), any(), any());
    }

    // ── queryAssignedIncidents ────────────────────────────────────────────────

    @Test
    void queryAssignedIncidents_agentFound_returnsPage() {
        Agent agent = Agent.builder().id("agent-1").userId("user-1").build();
        Page<Incident> page = new PageImpl<>(List.of(buildIncident()));
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.findByAssignedToIdUnified(eq("agent-1"), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Page<IncidentResponse> result = incidentService.queryAssignedIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(incidentRepository).findByAssignedToIdUnified(eq("agent-1"), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any());
    }

    @Test
    void queryAssignedIncidents_noAgentRecord_returnsEmptyPage() {
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        Page<IncidentResponse> result = incidentService.queryAssignedIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), Pageable.unpaged());

        assertThat(result.getTotalElements()).isZero();
        verify(incidentRepository, never()).findByAssignedToIdUnified(any(), any(), any(), any(), any());
    }

    // ── sort field translation ────────────────────────────────────────────────

    @Test
    void queryIncidents_sortByCategory_translatesToCategoryPath() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findByUserIdUnified(any(), any(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Pageable categorySort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "category"));
        incidentService.queryIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), categorySort);

        var captor = forClass(Pageable.class);
        verify(incidentRepository).findByUserIdUnified(eq("user-1"), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), captor.capture());
        Sort captured = captor.getValue().getSort();
        assertThat(captured.getOrderFor("incidentType.category.name")).isNotNull();
        assertThat(captured.getOrderFor("category")).isNull();
    }

    @Test
    void queryAllIncidents_sortByCategory_translatesToCategoryPath() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findAllUnified(any(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Pageable categorySort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "category"));
        incidentService.queryAllIncidents(null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), categorySort);

        var captor = forClass(Pageable.class);
        verify(incidentRepository).findAllUnified(isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), captor.capture());
        Sort captured = captor.getValue().getSort();
        assertThat(captured.getOrderFor("incidentType.category.name")).isNotNull();
        assertThat(captured.getOrderFor("category")).isNull();
    }

    @Test
    void queryDeptIncidents_sortByCategory_translatesToCategoryPath() {
        Agent agent = Agent.builder().id("agent-1").userId("user-1").build();
        Page<Incident> page = new PageImpl<>(List.of());
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(agentGroupMemberRepository.findAgentGroupIdsByAgentId("agent-1")).thenReturn(List.of("group-1"));
        when(agentGroupRepository.findDepartmentIdsByGroupIds(List.of("group-1"))).thenReturn(List.of("dept-1"));
        when(agentGroupRepository.findIdsByDepartmentIds(List.of("dept-1"))).thenReturn(List.of("group-1"));
        when(incidentRepository.findByDepartmentUnified(any(), any(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Pageable categorySort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "category"));
        incidentService.queryDeptIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), categorySort);

        var captor = forClass(Pageable.class);
        verify(incidentRepository).findByDepartmentUnified(any(), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), captor.capture());
        Sort captured = captor.getValue().getSort();
        assertThat(captured.getOrderFor("incidentType.category.name")).isNotNull();
        assertThat(captured.getOrderFor("category")).isNull();
    }

    @Test
    void queryAssignedIncidents_sortByCategory_translatesToCategoryPath() {
        Agent agent = Agent.builder().id("agent-1").userId("user-1").build();
        Page<Incident> page = new PageImpl<>(List.of());
        when(agentRepository.findByUserId("user-1")).thenReturn(Optional.of(agent));
        when(incidentRepository.findByAssignedToIdUnified(any(), any(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Pageable categorySort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "category"));
        incidentService.queryAssignedIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), categorySort);

        var captor = forClass(Pageable.class);
        verify(incidentRepository).findByAssignedToIdUnified(eq("agent-1"), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), captor.capture());
        Sort captured = captor.getValue().getSort();
        assertThat(captured.getOrderFor("incidentType.category.name")).isNotNull();
        assertThat(captured.getOrderFor("category")).isNull();
    }

    @Test
    void queryIncidents_sortByStatus_translatesToStatusPath() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findByUserIdUnified(any(), any(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Pageable statusSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "status"));
        incidentService.queryIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), statusSort);

        var captor = forClass(Pageable.class);
        verify(incidentRepository).findByUserIdUnified(eq("user-1"), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), captor.capture());
        Sort captured = captor.getValue().getSort();
        assertThat(captured.getOrderFor("status.name")).isNotNull();
        assertThat(captured.getOrderFor("status")).isNull();
    }

    @Test
    void queryIncidents_sortByPriority_translatesToSeverityPath() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findByUserIdUnified(any(), any(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Pageable prioritySort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "priority"));
        incidentService.queryIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), prioritySort);

        var captor = forClass(Pageable.class);
        verify(incidentRepository).findByUserIdUnified(eq("user-1"), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), captor.capture());
        Sort captured = captor.getValue().getSort();
        assertThat(captured.getOrderFor("severity.name")).isNotNull();
        assertThat(captured.getOrderFor("priority")).isNull();
    }

    @Test
    void queryIncidents_sortByUnknownField_fallsBackToDefault() {
        Page<Incident> page = new PageImpl<>(List.of());
        when(incidentRepository.findByUserIdUnified(any(), any(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), any()))
                .thenReturn(page);

        Pageable unknownSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "invalidField"));
        incidentService.queryIncidents("user-1", null, new IncidentFilterParams(null, null, null, null, null), new IncidentDateFilter(null, null), unknownSort);

        var captor = forClass(Pageable.class);
        verify(incidentRepository).findByUserIdUnified(eq("user-1"), isNull(), any(IncidentFilterParams.class), any(IncidentDateFilter.class), captor.capture());
        Sort captured = captor.getValue().getSort();
        assertThat(captured.getOrderFor("createdAt")).isNotNull();
    }
}
