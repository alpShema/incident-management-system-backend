package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.models.SlackUserMapping;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.slack.client.SlackClient;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import com.slack.api.model.view.Views;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true")
public class AppHomeService {

    private final SlackClient slackClient;
    private final SlackOAuthService oauthService;
    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final SlackProperties slackProperties;

    public void publishAppHome(String slackUserId) {
        Optional<SlackUserMapping> mapping = oauthService.findBySlackUserId(slackUserId);

        View view;
        if (mapping.isPresent()) {
            Optional<User> user = userRepository.findById(mapping.get().getHilfeUserId());
            view = buildConnectedHome(user.orElse(null));
        } else {
            view = buildDisconnectedHome();
        }

        slackClient.viewsPublish(slackUserId, view);
        log.debug("Published app home for user {}", slackUserId);
    }

    private View buildConnectedHome(User user) {
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

        // Notification settings — mirrors Jira's "Personal Notifications" button
        blocks.add(actions(a -> a.elements(List.of(
                button(b -> b
                        .actionId("open_notification_settings")
                        .text(plainText(pt -> pt.text("⚙️  Notification Settings").emoji(true)))
                )
        ))));

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
                        .style("primary")
                ),
                button(b -> b
                        .actionId("view_my_incidents")
                        .text(plainText("My Incidents"))
                )
        ))));

        // ── Agent section ─────────────────────────────────────────────────────
        if (slackProperties.agentFeaturesEnabled() && user != null && isAgent(user)) {
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

    private View buildDisconnectedHome() {
        List<LayoutBlock> blocks = new ArrayList<>();

        // ── Greeting ──────────────────────────────────────────────────────────
        blocks.add(section(s -> s.text(markdownText(
                "👋  Hi — Welcome to HILFE for Slack.\n"
                + "Here are a few ways you can get the most out of HILFE:"
        ))));

        blocks.add(divider());

        // ── Feature highlights ────────────────────────────────────────────────
        blocks.add(section(s -> s.text(markdownText(
                "✨  *Submit incidents*\n"
                + "Create and track IT support issues in seconds, right from Slack."
        ))));

        blocks.add(section(s -> s.text(markdownText(
                "🔔  *Real-time notifications*\n"
                + "Get notified the moment your ticket is assigned, updated, or resolved."
        ))));

        blocks.add(section(s -> s.text(markdownText(
                "📋  *Track your work*\n"
                + "View all your open incidents and monitor progress at a glance."
        ))));

        blocks.add(divider());

        // ── CTA ───────────────────────────────────────────────────────────────
        blocks.add(section(s -> s.text(markdownText(
                "🖥️  Ready to get started? Connect your HILFE account."
        ))));
        blocks.add(actions(a -> a.elements(List.of(
                button(b -> b
                        .actionId("connect_from_home")
                        .text(plainText("Connect to HILFE"))
                        .style("primary")
                )
        ))));

        blocks.add(divider());

        // ── Footer ────────────────────────────────────────────────────────────
        blocks.add(context(c -> c.elements(List.of(
                markdownText("<https://hilfe.amalitech.net|Open HILFE Web Platform>")
        ))));

        return Views.view(v -> v.type("home").blocks(blocks));
    }

    private boolean isAgent(User user) {
        String roleCode = user.getRoleCode();
        return "AGENT".equalsIgnoreCase(roleCode)
                || "ADMIN".equalsIgnoreCase(roleCode)
                || "SUPER_ADMIN".equalsIgnoreCase(roleCode);
    }
}
