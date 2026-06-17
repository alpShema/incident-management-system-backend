package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.models.SlackUserMapping;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.slack.client.SlackClient;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.BlockCompositions;
import com.slack.api.model.block.element.BlockElements;
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

        String userName = user != null ? user.getFullName() : "there";
        blocks.add(header(h -> h.text(plainText("HILFE"))));
        blocks.add(context(c -> c.elements(List.of(
                markdownText("Connected as " + userName)
        ))));

        blocks.add(divider());
        blocks.add(section(s -> s.text(markdownText("*Quick actions*"))));

        blocks.add(actions(a -> a.elements(List.of(
                button(b -> b
                        .actionId("create_incident")
                        .text(plainText(pt -> pt.text("New incident").emoji(true)))
                        .style("primary")
                ),
                button(b -> b
                        .actionId("view_my_incidents")
                        .text(plainText(pt -> pt.text("My incidents").emoji(true)))
                )
        ))));

        if (slackProperties.agentFeaturesEnabled() && user != null && isAgent(user)) {
            blocks.add(section(s -> s.text(markdownText("*Agent actions*"))));
            blocks.add(actions(a -> a.elements(List.of(
                    button(b -> b
                            .actionId("view_assigned_incidents")
                            .text(plainText(pt -> pt.text("Assigned incidents").emoji(true)))
                    )
            ))));
        }

        blocks.add(divider());
        blocks.add(context(c -> c.elements(List.of(
                markdownText("`/hilfe new` · `/hilfe my` · `/hilfe help`")
        ))));

        blocks.add(context(c -> c.elements(List.of(
                markdownText("Use `/hilfe disconnect` to unlink this account.")
        ))));

        return Views.view(v -> v
                .type("home")
                .blocks(blocks)
        );
    }

    private View buildDisconnectedHome() {
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(header(h -> h.text(plainText("HILFE"))));
        blocks.add(context(c -> c.elements(List.of(
                markdownText("Incident management from Slack.")
        ))));

        blocks.add(divider());
        blocks.add(section(s -> s.text(markdownText(
                "Connect your HILFE account to create and track incidents without leaving Slack."
        ))));

        blocks.add(section(s -> s.text(markdownText(
                "Run `/hilfe connect` to get started."
        ))));

        blocks.add(divider());
        blocks.add(context(c -> c.elements(List.of(
                markdownText("<https://hilfe.amalitech.net|Open HILFE web platform>")
        ))));

        return Views.view(v -> v
                .type("home")
                .blocks(blocks)
        );
    }

    private boolean isAgent(User user) {
        String roleCode = user.getRoleCode();
        return "AGENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode);
    }
}
