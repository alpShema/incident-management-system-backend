package com.amalitech.hilfe.slack;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.models.*;
import com.amalitech.hilfe.repositories.*;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.slack.client.SlackClient;
import com.amalitech.hilfe.slack.exception.SlackNotConnectedException;
import com.amalitech.hilfe.slack.exception.SlackPermissionDeniedException;
import com.amalitech.hilfe.slack.service.SlackAuditLogService;
import com.amalitech.hilfe.slack.service.SlackOAuthService;
import com.amalitech.hilfe.slack.service.IncidentModalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.api.model.view.View;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentModalServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-29T14:00:00Z");

    @Mock SlackClient slackClient;
    @Mock SlackOAuthService oauthService;
    @Mock SlackAuditLogService auditLogService;
    @Mock IncidentService incidentService;
    @Mock IncidentCategoryRepository incidentCategoryRepository;
    @Mock IncidentTypeRepository incidentTypeRepository;
    @Mock LocationRepository locationRepository;
    @Mock SeverityRepository severityRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock UserRepository userRepository;
    @Mock SlackProperties slackProperties;

    @InjectMocks
    IncidentModalService incidentModalService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SlackUserMapping mapping;
    private IncidentCategory category;
    private IncidentType incidentType;
    private Location location;
    private Severity severity;

    @BeforeEach
    void setUp() {
        mapping = SlackUserMapping.builder()
                .slackUserId("U_SLACK_001")
                .hilfeUserId("hilfe-user-1")
                .slackTeamId("T_TEAM_001")
                .connectedAt(FIXED_NOW)
                .build();

        category = new IncidentCategory();
        category.setId("cat-1");
        category.setName("IT");

        incidentType = new IncidentType();
        incidentType.setId("type-1");
        incidentType.setName("Network Issue");
        incidentType.setCategoryId("cat-1");

        location = new Location();
        location.setId("loc-1");
        location.setName("Accra");
        location.setStatus(true);

        severity = new Severity();
        severity.setId("sev-1");
        severity.setName("High");
        severity.setStatus(true);
    }

    // ── openCreateIncidentModal ──────────────────────────────────────────────

    @Test
    void openCreateIncidentModal_whenConnected_opensModal() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));
        when(incidentCategoryRepository.findByStatus(true)).thenReturn(List.of(category));
        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(severityRepository.findByStatus(true)).thenReturn(List.of(severity));

        incidentModalService.openCreateIncidentModal("U_SLACK_001", "trigger-1");

        verify(slackClient).viewsOpen(eq("trigger-1"), any(View.class));
    }

    @Test
    void openCreateIncidentModal_logsAuditEvent() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));
        when(incidentCategoryRepository.findByStatus(true)).thenReturn(List.of());
        when(locationRepository.findAll()).thenReturn(List.of());
        when(severityRepository.findByStatus(true)).thenReturn(List.of());

        incidentModalService.openCreateIncidentModal("U_SLACK_001", "trigger-1");

        verify(auditLogService).log(
                eq("INCIDENT_MODAL_OPENED"), eq("U_SLACK_001"), eq("hilfe-user-1"),
                eq("MODAL"), isNull(), any());
    }

    @Test
    void openCreateIncidentModal_whenNotConnected_throwsException() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentModalService.openCreateIncidentModal("U_SLACK_001", "trigger-1"))
                .isInstanceOf(SlackNotConnectedException.class);

        verifyNoInteractions(slackClient);
    }

    // ── handleCategorySelection ──────────────────────────────────────────────

    @Test
    void handleCategorySelection_withValidCategory_updatesModalWithTopics() {
        when(incidentCategoryRepository.findByStatus(true)).thenReturn(List.of(category));
        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(severityRepository.findByStatus(true)).thenReturn(List.of(severity));
        when(incidentTypeRepository.findByCategoryIdWithAgentAndStatus("cat-1", true))
                .thenReturn(List.of(incidentType));

        JsonNode payload = buildCategorySelectionPayload("cat-1", "IT", "V_VIEW_001", "My Title", "My Desc");

        incidentModalService.handleCategorySelection(payload);

        verify(slackClient).viewsUpdate(eq("V_VIEW_001"), any(View.class));
    }

    @Test
    void handleCategorySelection_withNullCategory_updatesModalWithEmptyTopics() {
        when(incidentCategoryRepository.findByStatus(true)).thenReturn(List.of(category));
        when(locationRepository.findAll()).thenReturn(List.of(location));
        when(severityRepository.findByStatus(true)).thenReturn(List.of(severity));

        JsonNode payload = buildCategorySelectionPayload(null, null, "V_VIEW_001", "Title", "Desc");

        incidentModalService.handleCategorySelection(payload);

        verify(incidentTypeRepository, never()).findByCategoryIdWithAgentAndStatus(any(), any());
        verify(slackClient).viewsUpdate(eq("V_VIEW_001"), any(View.class));
    }

    // ── handleCreateIncidentSubmission ───────────────────────────────────────

    @Test
    void handleCreateIncidentSubmission_withValidPayload_createsIncidentAndReturnEmpty() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));

        IncidentResponse response = mock(IncidentResponse.class);
        when(response.incidentNo()).thenReturn(42);
        when(response.title()).thenReturn("Test Incident");
        when(response.id()).thenReturn("inc-1");
        LookupResponse status = new LookupResponse("status-open", "Open");
        when(response.status()).thenReturn(status);

        when(incidentService.createIncident(eq("hilfe-user-1"), any(CreateIncidentRequest.class)))
                .thenReturn(response);

        JsonNode payload = buildSubmissionPayload(
                "U_SLACK_001", "Test Incident", "Reproducing steps here",
                "cat-1", "type-1", "loc-1");

        String result = incidentModalService.handleCreateIncidentSubmission(payload);

        assertThat(result).isEmpty();
        verify(incidentService).createIncident(eq("hilfe-user-1"), any(CreateIncidentRequest.class));
        verify(slackClient).chatPostMessage(eq("U_SLACK_001"), anyString());
    }

    @Test
    void handleCreateIncidentSubmission_passesCorrectFieldsToService() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));

        IncidentResponse response = mock(IncidentResponse.class);
        when(response.incidentNo()).thenReturn(1);
        when(response.title()).thenReturn("T");
        when(response.id()).thenReturn("i-1");
        when(response.status()).thenReturn(new LookupResponse("s", "Open"));
        when(incidentService.createIncident(any(), any())).thenReturn(response);

        JsonNode payload = buildSubmissionPayload(
                "U_SLACK_001", "My Title", "Detailed description", "cat-1", "type-1", "loc-1");

        incidentModalService.handleCreateIncidentSubmission(payload);

        var captor = org.mockito.ArgumentCaptor.forClass(CreateIncidentRequest.class);
        verify(incidentService).createIncident(any(), captor.capture());
        CreateIncidentRequest req = captor.getValue();
        assertThat(req.title()).isEqualTo("My Title");
        assertThat(req.description()).isEqualTo("Detailed description");
        assertThat(req.incidentTypeId()).isEqualTo("type-1");
        assertThat(req.locationId()).isEqualTo("loc-1");
    }

    @Test
    void handleCreateIncidentSubmission_withMissingTitle_returnsValidationError() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));

        JsonNode payload = buildSubmissionPayload(
                "U_SLACK_001", "", "Description", "cat-1", "type-1", "loc-1");

        String result = incidentModalService.handleCreateIncidentSubmission(payload);

        assertThat(result).contains("response_action").contains("errors").contains("title_block");
        verifyNoInteractions(incidentService);
    }

    @Test
    void handleCreateIncidentSubmission_withMissingTopic_returnsValidationError() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));

        JsonNode payload = buildSubmissionPayload(
                "U_SLACK_001", "Title", "Description", "cat-1", null, "loc-1");

        String result = incidentModalService.handleCreateIncidentSubmission(payload);

        assertThat(result).contains("response_action").contains("errors");
        verifyNoInteractions(incidentService);
    }

    @Test
    void handleCreateIncidentSubmission_withMissingLocation_returnsValidationError() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));

        JsonNode payload = buildSubmissionPayload(
                "U_SLACK_001", "Title", "Description", "cat-1", "type-1", null);

        String result = incidentModalService.handleCreateIncidentSubmission(payload);

        assertThat(result).contains("response_action").contains("errors").contains("location_block");
        verifyNoInteractions(incidentService);
    }

    @Test
    void handleCreateIncidentSubmission_withMissingDescription_returnsValidationError() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));

        JsonNode payload = buildSubmissionPayload(
                "U_SLACK_001", "Title", "", "cat-1", "type-1", "loc-1");

        String result = incidentModalService.handleCreateIncidentSubmission(payload);

        assertThat(result).contains("response_action").contains("errors").contains("description_block");
        verifyNoInteractions(incidentService);
    }

    @Test
    void handleCreateIncidentSubmission_whenServiceThrows_returnsErrorResponse() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));
        when(incidentService.createIncident(any(), any()))
                .thenThrow(new RuntimeException("DB connection failed"));

        JsonNode payload = buildSubmissionPayload(
                "U_SLACK_001", "Title", "Description", "cat-1", "type-1", "loc-1");

        String result = incidentModalService.handleCreateIncidentSubmission(payload);

        assertThat(result).contains("response_action").contains("errors");
    }

    @Test
    void handleCreateIncidentSubmission_whenNotConnected_throwsException() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.empty());

        JsonNode payload = buildSubmissionPayload(
                "U_SLACK_001", "Title", "Description", "cat-1", "type-1", "loc-1");

        assertThatThrownBy(() -> incidentModalService.handleCreateIncidentSubmission(payload))
                .isInstanceOf(SlackNotConnectedException.class);
    }

    // ── openMyIncidentsModal ─────────────────────────────────────────────────

    @Test
    void openMyIncidentsModal_whenConnected_opensModal() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));
        Page<IncidentResponse> page = new PageImpl<>(List.of());
        when(incidentService.queryIncidents(any(), any(), any(), any(), any())).thenReturn(page);

        incidentModalService.openMyIncidentsModal("U_SLACK_001", "trigger-1");

        verify(slackClient).viewsOpen(eq("trigger-1"), any(View.class));
    }

    @Test
    void openMyIncidentsModal_whenNotConnected_throwsException() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentModalService.openMyIncidentsModal("U_SLACK_001", "trigger-1"))
                .isInstanceOf(SlackNotConnectedException.class);

        verifyNoInteractions(slackClient);
    }

    // ── openAssignedIncidentsModal ───────────────────────────────────────────

    @Test
    void openAssignedIncidentsModal_whenAgent_opensModal() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));
        User agentUser = User.builder().id("hilfe-user-1").roleCode(RoleCode.AGENT).build();
        when(userRepository.findById("hilfe-user-1")).thenReturn(Optional.of(agentUser));
        Page<IncidentResponse> page = new PageImpl<>(List.of());
        when(incidentService.queryAssignedIncidents(any(), any(), any(), any(), any())).thenReturn(page);

        incidentModalService.openAssignedIncidentsModal("U_SLACK_001", "trigger-1");

        verify(slackClient).viewsOpen(eq("trigger-1"), any(View.class));
    }

    @Test
    void openAssignedIncidentsModal_whenAdmin_opensModal() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));
        User adminUser = User.builder().id("hilfe-user-1").roleCode(RoleCode.ADMIN).build();
        when(userRepository.findById("hilfe-user-1")).thenReturn(Optional.of(adminUser));
        Page<IncidentResponse> page = new PageImpl<>(List.of());
        when(incidentService.queryAssignedIncidents(any(), any(), any(), any(), any())).thenReturn(page);

        incidentModalService.openAssignedIncidentsModal("U_SLACK_001", "trigger-1");

        verify(slackClient).viewsOpen(eq("trigger-1"), any(View.class));
    }

    @Test
    void openAssignedIncidentsModal_whenNotAgent_throwsPermissionDenied() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));
        User clientUser = User.builder().id("hilfe-user-1").roleCode(RoleCode.CLIENT).build();
        when(userRepository.findById("hilfe-user-1")).thenReturn(Optional.of(clientUser));

        assertThatThrownBy(() -> incidentModalService.openAssignedIncidentsModal("U_SLACK_001", "trigger-1"))
                .isInstanceOf(SlackPermissionDeniedException.class);

        verifyNoInteractions(slackClient);
    }

    @Test
    void openAssignedIncidentsModal_whenUserNotFound_throwsPermissionDenied() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.of(mapping));
        when(userRepository.findById("hilfe-user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentModalService.openAssignedIncidentsModal("U_SLACK_001", "trigger-1"))
                .isInstanceOf(SlackPermissionDeniedException.class);
    }

    @Test
    void openAssignedIncidentsModal_whenNotConnected_throwsException() {
        when(oauthService.findBySlackUserId("U_SLACK_001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentModalService.openAssignedIncidentsModal("U_SLACK_001", "trigger-1"))
                .isInstanceOf(SlackNotConnectedException.class);
    }

    // ── Payload builders ─────────────────────────────────────────────────────

    private JsonNode buildCategorySelectionPayload(String categoryId, String categoryName,
                                                    String viewId, String title, String description) {
        ObjectNode payload = MAPPER.createObjectNode();

        ObjectNode action = payload.putArray("actions").addObject();
        if (categoryId != null) {
            ObjectNode selectedOption = action.putObject("selected_option");
            selectedOption.put("value", categoryId);
            selectedOption.putObject("text").put("text", categoryName);
        } else {
            action.putNull("selected_option");
        }

        ObjectNode view = payload.putObject("view");
        view.put("id", viewId);
        ObjectNode values = view.putObject("state").putObject("values");

        values.putObject("title_block").putObject("title_input").put("value", title);
        values.putObject("description_block").putObject("description_input").put("value", description);
        values.putObject("location_block").putObject("location_select").putNull("selected_option");
        values.putObject("severity_block").putObject("severity_select").putNull("selected_option");

        return payload;
    }

    private JsonNode buildSubmissionPayload(String slackUserId, String title, String description,
                                             String categoryId, String topicId, String locationId) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.putObject("user").put("id", slackUserId);

        ObjectNode values = payload.putObject("view").putObject("state").putObject("values");

        values.putObject("title_block").putObject("title_input").put("value", title);
        values.putObject("description_block").putObject("description_input").put("value", description);
        values.putObject("severity_block").putObject("severity_select").putNull("selected_option");

        putSelectValue(values, "category_block", "category_select", categoryId);
        putSelectValue(values, "topic_block", "topic_select", topicId);
        putSelectValue(values, "location_block", "location_select", locationId);

        return payload;
    }

    private void putSelectValue(ObjectNode values, String blockId, String actionId, String value) {
        ObjectNode action = values.putObject(blockId).putObject(actionId);
        if (value != null) {
            action.putObject("selected_option").put("value", value);
        } else {
            action.putNull("selected_option");
        }
    }
}
