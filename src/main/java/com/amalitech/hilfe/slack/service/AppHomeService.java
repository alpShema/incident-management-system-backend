package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.models.SlackNotificationPreference;
import com.amalitech.hilfe.models.SlackUserMapping;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import com.amalitech.hilfe.slack.client.SlackClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.GrantedAuthority;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewClose;
import com.slack.api.model.view.ViewSubmit;
import com.slack.api.model.view.ViewTitle;
import com.slack.api.model.view.Views;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.slack.api.model.block.Blocks;
import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true")
public class AppHomeService {

    private static final String STYLE_PRIMARY = "primary";
    private static final String PLAIN_TEXT = "plain_text";

    @Value("${app.base-url:https://hilfe.amalitech.net}")
    private String appBaseUrl;

    @Value("${app.slack-image-report-url:https://i.ibb.co/VYXTtTTF/screenshot1.jpg}")
    private String slackImageReportUrl;

    @Value("${app.slack-image-updates-url:https://i.ibb.co/rRbTY5S5/screenshot2.jpg}")
    private String slackImageUpdatesUrl;

    private final SlackClient slackClient;
    private final SlackOAuthService oauthService;
    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final SlackNotificationPreferenceService preferenceService;
    private final SlackProperties slackProperties;
    private final UserAuthorityService userAuthorityService;

    public void publishAppHome(String slackUserId) {
        Optional<SlackUserMapping> mapping = oauthService.findBySlackUserId(slackUserId);

        View view;
        if (mapping.isPresent()) {
            Optional<User> user = userRepository.findById(mapping.get().getHilfeUserId());
            view = buildConnectedHome(user.orElse(null), mapping.get().getHilfeUserId());
        } else {
            view = buildDisconnectedHome(slackUserId);
        }

        slackClient.viewsPublish(slackUserId, view);
        log.debug("Published app home for user {}", slackUserId);
    }

    private View buildConnectedHome(User user, String hilfeUserId) {
        List<LayoutBlock> blocks = new ArrayList<>();

        String name = user != null ? user.getFullName() : "there";
        String email = user != null && user.getEmail() != null ? user.getEmail() : "";
        long incidentCount = user != null ? incidentRepository.countByUserId(user.getId()) : 0;

        // ── Greeting ─────────────────────────────────────────────────────────
        blocks.add(section(s -> s.text(markdownText(
                "👋  Hi, *" + name + "* — Welcome to HILFE for Slack.\n"
                + "HILFE allows you to create incidents, view your work, and manage notification preferences."
        ))));

        blocks.add(divider());

        // ── Connected status ──────────────────────────────────────────────────
        String statusLine = "✅  *Connected to HILFE*\n"
                + name + (email.isBlank() ? "" : "  ·  " + email);
        blocks.add(section(s -> s.text(markdownText(statusLine))));

        blocks.add(divider());

        // ── Work section ──────────────────────────────────────────────────────
        blocks.add(section(s -> s.text(markdownText("Your work in HILFE"))));
        blocks.add(section(s -> s.text(markdownText(
                "View your incidents and interact with them using the buttons below. "
                + "You have *" + incidentCount + "* incident" + (incidentCount == 1 ? "" : "s") + " on record. "
                + "Create new ones in any channel using `/hilfe new`."
        ))));

        blocks.add(actions(a -> a.elements(List.of(
                button(b -> b
                        .actionId("create_incident")
                        .text(plainText("New Incident"))
                        .style(STYLE_PRIMARY)
                ),
                button(b -> b
                        .actionId("view_my_incidents")
                        .text(plainText("My Incidents"))
                )
        ))));

        // ── Agent section ─────────────────────────────────────────────────────
        if (slackProperties.agentFeaturesEnabled() && hilfeUserId != null
                && hasPermission(hilfeUserId, RbacPermissions.INCIDENT_READ_ASSIGNED)) {
            blocks.add(divider());
            blocks.add(section(s -> s.text(markdownText("Assigned to you"))));
            blocks.add(section(s -> s.text(markdownText(
                    "View incidents assigned to you and manage your queue."
            ))));
            blocks.add(actions(a -> a.elements(List.of(
                    button(b -> b
                            .actionId("view_assigned_incidents")
                            .text(plainText("Assigned Incidents"))
                    )
            ))));
        }

        blocks.add(divider());

        // ── Notification preferences ──────────────────────────────────────────
        blocks.add(actions(a -> a.elements(List.of(
                button(b -> b
                        .actionId("open_notification_settings")
                        .text(plainText(pt -> pt.text("⚙️  Notification Settings").emoji(true)))
                )
        ))));

        blocks.add(divider());

        // ── Account management ────────────────────────────────────────────────
        blocks.add(section(s -> s.text(markdownText("*Account*"))));
        blocks.add(actions(a -> a.elements(List.of(
                button(b -> b
                        .actionId("disconnect_from_home")
                        .text(plainText("Disconnect"))
                        .style("danger")
                )
        ))));

        blocks.add(divider());

        // ── Commands ──────────────────────────────────────────────────────────
        blocks.add(section(s -> s.text(markdownText("Commands"))));
        blocks.add(section(s -> s.fields(List.of(
                markdownText("`/hilfe new`\nCreate a new incident"),
                markdownText("`/hilfe my`\nView your incidents"),
                markdownText("`/hilfe assigned`\nView assigned incidents"),
                markdownText("`/hilfe settings`\nNotification preferences"),
                markdownText("`/hilfe help`\nShow all commands"),
                markdownText("`/hilfe disconnect`\nUnlink your account")
        ))));

        blocks.add(divider());

        // ── Footer ────────────────────────────────────────────────────────────
        blocks.add(context(c -> c.elements(List.of(
                markdownText("<https://hilfe.amalitech.net|Open HILFE>  ·  Use `/hilfe disconnect` to unlink your account")
        ))));

        return Views.view(v -> v.type("home").blocks(blocks));
    }

    // Slack checkboxes element allows at most 10 options — split across two groups
    private static final List<String> NOTIF_GROUP_1 = List.of(
            "INCIDENT_ASSIGNED", "INCIDENT_ESCALATED", "INCIDENT_STATUS_CHANGED",
            "INCIDENT_PENDING", "INCIDENT_REOPENED", "INCIDENT_PRIORITY_CHANGED",
            "INCIDENT_UNASSIGNED"
    );
    private static final List<String> NOTIF_GROUP_2 = List.of(
            "INCIDENT_AUTO_CLOSED", "INCIDENT_AUTO_CLOSED_CLIENT",
            "INCIDENT_AUTO_ASSIGNED_CLIENT", "INCIDENT_REASSIGNED_CLIENT",
            "INCIDENT_SLA_AT_RISK", "INCIDENT_SLA_BREACHED"
    );

    public void openNotificationSettingsModal(String slackUserId, String triggerId, String hilfeUserId) {
        // Single DB query — avoids exhausting the 3-second trigger_id window
        Set<String> disabledTypes = preferenceService.getPreferences(hilfeUserId).stream()
                .filter(p -> Boolean.FALSE.equals(p.getEnabled()))
                .map(SlackNotificationPreference::getNotificationType)
                .collect(Collectors.toSet());

        List<LayoutBlock> blocks = List.of(
                section(s -> s.text(markdownText("Choose which events send you a Slack DM."))),
                buildCheckboxBlock("notif_prefs_block_1", "notif_types_1",
                        "Incident Events", NOTIF_GROUP_1, disabledTypes),
                buildCheckboxBlock("notif_prefs_block_2", "notif_types_2",
                        "Automated & SLA Events", NOTIF_GROUP_2, disabledTypes)
        );

        View modal = Views.view(v -> v
                .type("modal")
                .callbackId("notification_settings")
                .title(ViewTitle.builder().type(PLAIN_TEXT).text("Notification Settings").emoji(true).build())
                .submit(ViewSubmit.builder().type(PLAIN_TEXT).text("Save").emoji(true).build())
                .close(ViewClose.builder().type(PLAIN_TEXT).text("Cancel").emoji(true).build())
                .blocks(blocks)
        );

        slackClient.viewsOpen(triggerId, modal);
        log.debug("Opened notification settings modal for Slack user {}", slackUserId);
    }

    private LayoutBlock buildCheckboxBlock(String blockId, String actionId, String label,
                                           List<String> types, Set<String> disabledTypes) {
        List<OptionObject> all = types.stream()
                .map(type -> option(plainText(notifLabel(type)), type))
                .toList();
        List<OptionObject> enabled = types.stream()
                .filter(type -> !disabledTypes.contains(type))
                .map(type -> option(plainText(notifLabel(type)), type))
                .toList();

        return input(i -> i
                .blockId(blockId)
                .label(plainText(label))
                .optional(true)
                .element(checkboxes(c -> {
                    c.actionId(actionId).options(all);
                    if (!enabled.isEmpty()) c.initialOptions(enabled);
                    return c;
                }))
        );
    }

    public void handleNotificationSettingsSubmission(JsonNode payload, String hilfeUserId) {
        String slackUserId = payload.path("user").path("id").asText();

        JsonNode values = payload.path("view").path("state").path("values");
        Set<String> enabled = new HashSet<>();
        for (JsonNode opt : values.path("notif_prefs_block_1").path("notif_types_1").path("selected_options")) {
            enabled.add(opt.path("value").asText());
        }
        for (JsonNode opt : values.path("notif_prefs_block_2").path("notif_types_2").path("selected_options")) {
            enabled.add(opt.path("value").asText());
        }

        for (String type : SlackNotificationPreferenceService.NOTIFICATION_TYPES) {
            preferenceService.updatePreference(hilfeUserId, type, enabled.contains(type));
        }

        publishAppHome(slackUserId);
        log.debug("Saved notification preferences for HILFE user {}", hilfeUserId);
    }

    private static String notifLabel(String type) {
        return switch (type) {
            case "INCIDENT_ASSIGNED"             -> "New Assignment";
            case "INCIDENT_ESCALATED"            -> "Escalation Alert";
            case "INCIDENT_STATUS_CHANGED"       -> "Status Updates";
            case "INCIDENT_PENDING"              -> "Pending Notice";
            case "INCIDENT_REOPENED"             -> "Incident Reopened";
            case "INCIDENT_PRIORITY_CHANGED"     -> "Priority Changes";
            case "INCIDENT_UNASSIGNED"           -> "Reassignment";
            case "INCIDENT_AUTO_CLOSED"          -> "Auto-Closed (Agent)";
            case "INCIDENT_AUTO_CLOSED_CLIENT"   -> "Auto-Closed";
            case "INCIDENT_AUTO_ASSIGNED_CLIENT" -> "Agent Assigned";
            case "INCIDENT_REASSIGNED_CLIENT"    -> "New Agent";
            case "INCIDENT_SLA_AT_RISK"          -> "SLA At Risk";
            case "INCIDENT_SLA_BREACHED"         -> "SLA Breached";
            default                              -> type;
        };
    }

    private View buildDisconnectedHome(String slackUserId) {
        List<LayoutBlock> blocks = new ArrayList<>();

        // ── Greeting ──────────────────────────────────────────────────────────
        blocks.add(section(s -> s.text(markdownText(
                "👋 *Hi <@" + slackUserId + "> — Welcome to HILFE for Slack*"))));
        blocks.add(section(s -> s.text(markdownText(
                "Here are a few ways you can get the most out of HILFE:"))));

        blocks.add(divider());

        // ── Feature 1: Report ─────────────────────────────────────────────────
        blocks.add(Blocks.image(i -> i
                .imageUrl(slackImageReportUrl)
                .altText("Reporting an incident from Slack")));
        blocks.add(section(s -> s.text(markdownText(
                ":memo: *Report incidents from Slack*\n"
                + "Create an incident with `/hilfe new` and it's logged in HILFE instantly."))));

        blocks.add(divider());

        // ── Feature 2: Notifications ─────────────────────────────────────────
        blocks.add(Blocks.image(i -> i
                .imageUrl(slackImageUpdatesUrl)
                .altText("Incident update notification in Slack")));
        blocks.add(section(s -> s.text(markdownText(
                ":bell: *Stay updated*\n"
                + "Get notified when your incident is assigned, updated, or resolved."))));

        blocks.add(divider());

        // ── CTA ───────────────────────────────────────────────────────────────
        blocks.add(section(s -> s.text(markdownText(
                ":inbox_tray: *Ready to get started?* Connect your HILFE account to begin."))));
        blocks.add(actions(a -> a.elements(List.of(
                button(b -> b
                        .actionId("connect_from_home")
                        .text(plainText(pt -> pt.text("Connect account").emoji(true)))
                        .style(STYLE_PRIMARY)
                ),
                button(b -> b
                        .actionId("create_incident")
                        .text(plainText(pt -> pt.text("Report an incident").emoji(true)))
                ),
                button(b -> b
                        .actionId("view_my_incidents")
                        .text(plainText(pt -> pt.text("View my incidents").emoji(true)))
                )
        ))));

        return Views.view(v -> v.type("home").blocks(blocks));
    }

    private boolean hasPermission(String hilfeUserId, String permissionCode) {
        return userAuthorityService.resolveByUserId(hilfeUserId)
                .map(resolved -> resolved.authorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(permissionCode::equals))
                .orElse(false);
    }
}
