package com.amalitech.hilfe.slack;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.models.SlackUserMapping;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import com.amalitech.hilfe.slack.client.SlackClient;
import com.amalitech.hilfe.slack.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackEventHandlerTest {

    @Mock private SlackClient slackClient;
    @Mock private SlackOAuthService oauthService;
    @Mock private SlackAuditLogService auditLogService;
    @Mock private AppHomeService appHomeService;
    @Mock private IncidentModalService incidentModalService;
    @Mock private SlackProperties slackProperties;
    @Mock private UserAuthorityService userAuthorityService;

    @InjectMocks
    private SlackEventHandler handler;

    private final ObjectMapper mapper = new ObjectMapper();

    // ── handleEvent ───────────────────────────────────────────────────────────

    @Test
    void handleEvent_appHomeOpened_publishesAppHome() {
        ObjectNode event = mapper.createObjectNode();
        event.put("user", "U_SLACK_001");

        handler.handleEvent("app_home_opened", event);

        verify(appHomeService).publishAppHome("U_SLACK_001");
    }

    @Test
    void handleEvent_appHomeOpened_serviceThrows_doesNotPropagate() {
        ObjectNode event = mapper.createObjectNode();
        event.put("user", "U_SLACK_001");
        doThrow(new RuntimeException("Slack API down")).when(appHomeService).publishAppHome(any());

        handler.handleEvent("app_home_opened", event);

        verify(appHomeService).publishAppHome("U_SLACK_001");
    }

    @Test
    void handleEvent_unknownType_isIgnored() {
        ObjectNode event = mapper.createObjectNode();
        event.put("user", "U1");

        handler.handleEvent("reaction_added", event);

        verifyNoInteractions(appHomeService, incidentModalService, slackClient);
    }

    // ── handleCommand – connect ───────────────────────────────────────────────

    @Test
    void handleCommand_connect_whenNotConnected_returnsOAuthUrl() {
        when(oauthService.isConnected("U1")).thenReturn(false);
        when(oauthService.generateOAuthState("U1", "T1")).thenReturn("state-123");
        when(oauthService.buildOAuthUrl("state-123")).thenReturn("https://connect.test?state=state-123");

        String response = handler.handleCommand("connect", "U1", "trigger-1", "T1");

        assertThat(response).contains("https://connect.test?state=state-123");
    }

    @Test
    void handleCommand_connect_whenAlreadyConnected_returnsAlreadyConnectedMessage() {
        when(oauthService.isConnected("U1")).thenReturn(true);

        String response = handler.handleCommand("connect", "U1", "trigger-1", "T1");

        assertThat(response).contains("already connected");
    }

    // ── handleCommand – disconnect ────────────────────────────────────────────

    @Test
    void handleCommand_disconnect_whenConnected_disconnectsUser() {
        when(oauthService.isConnected("U1")).thenReturn(true);

        String response = handler.handleCommand("disconnect", "U1", "trigger-1", "T1");

        verify(oauthService).disconnectUser("U1");
        assertThat(response).contains("disconnected");
    }

    @Test
    void handleCommand_disconnect_whenNotConnected_returnsNotConnectedMessage() {
        when(oauthService.isConnected("U1")).thenReturn(false);

        String response = handler.handleCommand("disconnect", "U1", "trigger-1", "T1");

        verify(oauthService, never()).disconnectUser(any());
        assertThat(response).contains("not connected");
    }

    // ── handleCommand – new incident ──────────────────────────────────────────

    @Test
    void handleCommand_new_whenConnected_opensModal() {
        when(oauthService.isConnected("U1")).thenReturn(true);

        String response = handler.handleCommand("new", "U1", "trigger-1", "T1");

        verify(incidentModalService).openCreateIncidentModal("U1", "trigger-1");
        assertThat(response).isEmpty();
    }

    @Test
    void handleCommand_create_isAliasForNew() {
        when(oauthService.isConnected("U1")).thenReturn(true);

        handler.handleCommand("create", "U1", "trigger-1", "T1");

        verify(incidentModalService).openCreateIncidentModal("U1", "trigger-1");
    }

    @Test
    void handleCommand_new_whenNotConnected_returnsConnectFirst() {
        when(oauthService.isConnected("U1")).thenReturn(false);

        String response = handler.handleCommand("new", "U1", "trigger-1", "T1");

        verifyNoInteractions(incidentModalService);
        assertThat(response).contains("connect");
    }

    @Test
    void handleCommand_new_modalThrows_returnsErrorMessage() {
        when(oauthService.isConnected("U1")).thenReturn(true);
        doThrow(new RuntimeException("Slack error")).when(incidentModalService)
                .openCreateIncidentModal(any(), any());

        String response = handler.handleCommand("new", "U1", "trigger-1", "T1");

        assertThat(response).contains("Failed");
    }

    // ── handleCommand – my incidents ──────────────────────────────────────────

    @Test
    void handleCommand_my_whenConnected_opensModal() {
        when(oauthService.isConnected("U1")).thenReturn(true);

        String response = handler.handleCommand("my", "U1", "trigger-1", "T1");

        verify(incidentModalService).openMyIncidentsModal("U1", "trigger-1");
        assertThat(response).isEmpty();
    }

    @Test
    void handleCommand_list_isAliasForMy() {
        when(oauthService.isConnected("U1")).thenReturn(true);

        handler.handleCommand("list", "U1", "trigger-1", "T1");

        verify(incidentModalService).openMyIncidentsModal("U1", "trigger-1");
    }

    // ── handleCommand – assigned ──────────────────────────────────────────────

    @Test
    void handleCommand_assigned_whenConnectedAndFeatureEnabled_opensModal() {
        SlackUserMapping mapping = SlackUserMapping.builder()
                .slackUserId("U1").hilfeUserId("h1").build();
        var resolved = new UserAuthorityService.ResolvedAuthorities(
                "h1", "agent@test.com", "AGENT",
                List.of(new SimpleGrantedAuthority("incident.read.assigned")), 1);
        when(oauthService.isConnected("U1")).thenReturn(true);
        when(slackProperties.agentFeaturesEnabled()).thenReturn(true);
        when(oauthService.findBySlackUserId("U1")).thenReturn(Optional.of(mapping));
        when(userAuthorityService.resolveByUserId("h1")).thenReturn(Optional.of(resolved));

        String response = handler.handleCommand("assigned", "U1", "trigger-1", "T1");

        verify(incidentModalService).openAssignedIncidentsModal("U1", "trigger-1");
        assertThat(response).isEmpty();
    }

    @Test
    void handleCommand_assigned_whenUserLacksPermission_returnsNotAllowed() {
        when(oauthService.isConnected("U1")).thenReturn(true);
        when(slackProperties.agentFeaturesEnabled()).thenReturn(true);
        when(oauthService.findBySlackUserId("U1")).thenReturn(Optional.empty());

        String response = handler.handleCommand("assigned", "U1", "trigger-1", "T1");

        verifyNoInteractions(incidentModalService);
        assertThat(response).contains("permission");
    }

    @Test
    void handleCommand_assigned_whenFeatureDisabled_returnsNotEnabled() {
        when(oauthService.isConnected("U1")).thenReturn(true);
        when(slackProperties.agentFeaturesEnabled()).thenReturn(false);

        String response = handler.handleCommand("assigned", "U1", "trigger-1", "T1");

        verifyNoInteractions(incidentModalService);
        assertThat(response).contains("not enabled");
    }

    // ── handleCommand – help ──────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"help", "", "unknown-cmd"})
    void handleCommand_helpVariants_returnHelpResponse(String text) {
        String response = handler.handleCommand(text, "U1", "trigger-1", "T1");
        assertThat(response).contains("HILFE Bot Commands");
    }

    // ── handleInteraction – view_submission ───────────────────────────────────

    @Test
    void handleInteraction_viewSubmission_createIncident_delegatesToModalService() {
        ObjectNode view = mapper.createObjectNode();
        view.put("callback_id", "create_incident");
        ObjectNode user = mapper.createObjectNode();
        user.put("id", "U1");
        ObjectNode payload = mapper.createObjectNode();
        payload.set("view", view);
        payload.set("user", user);

        when(incidentModalService.handleCreateIncidentSubmission(any())).thenReturn("");

        String result = handler.handleInteraction("view_submission", payload);

        verify(incidentModalService).handleCreateIncidentSubmission(payload);
        assertThat(result).isEmpty();
    }

    @Test
    void handleInteraction_unknownType_returnsEmpty() {
        ObjectNode payload = mapper.createObjectNode();
        String result = handler.handleInteraction("unknown_type", payload);
        assertThat(result).isEmpty();
    }

    @Test
    void handleInteraction_viewClosed_returnsEmpty() {
        ObjectNode view = mapper.createObjectNode();
        view.put("callback_id", "create_incident");
        ObjectNode payload = mapper.createObjectNode();
        payload.set("view", view);

        String result = handler.handleInteraction("view_closed", payload);

        assertThat(result).isEmpty();
    }

    // ── handleInteraction – block_actions ────────────────────────────────────

    @Test
    void handleInteraction_blockActions_createIncident_opensModal() {
        when(oauthService.isConnected("U1")).thenReturn(true);
        ObjectNode action = mapper.createObjectNode();
        action.put("action_id", "create_incident");
        ObjectNode user = mapper.createObjectNode();
        user.put("id", "U1");
        ObjectNode payload = mapper.createObjectNode();
        payload.set("user", user);
        payload.put("trigger_id", "trigger-1");
        payload.set("actions", mapper.createArrayNode().add(action));

        handler.handleInteraction("block_actions", payload);

        verify(incidentModalService).openCreateIncidentModal("U1", "trigger-1");
    }

    @Test
    void handleInteraction_blockActions_categorySelect_delegatesToModalService() {
        ObjectNode action = mapper.createObjectNode();
        action.put("action_id", "category_select");
        ObjectNode user = mapper.createObjectNode();
        user.put("id", "U1");
        ObjectNode payload = mapper.createObjectNode();
        payload.set("user", user);
        payload.put("trigger_id", "trigger-1");
        payload.set("actions", mapper.createArrayNode().add(action));

        handler.handleInteraction("block_actions", payload);

        verify(incidentModalService).handleCategorySelection(payload);
    }

    @Test
    void handleInteraction_blockActions_openNotificationSettings_opensModal() {
        SlackUserMapping mapping = SlackUserMapping.builder()
                .slackUserId("U1").hilfeUserId("h1").build();

        ObjectNode action = mapper.createObjectNode();
        action.put("action_id", "open_notification_settings");
        ObjectNode user = mapper.createObjectNode();
        user.put("id", "U1");
        ObjectNode payload = mapper.createObjectNode();
        payload.set("user", user);
        payload.put("trigger_id", "trigger-1");
        payload.set("actions", mapper.createArrayNode().add(action));

        when(oauthService.findBySlackUserId("U1")).thenReturn(Optional.of(mapping));

        handler.handleInteraction("block_actions", payload);

        verify(appHomeService).openNotificationSettingsModal("U1", "trigger-1", "h1");
    }
}
