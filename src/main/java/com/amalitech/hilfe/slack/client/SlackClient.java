package com.amalitech.hilfe.slack.client;

import com.amalitech.hilfe.config.SlackProperties;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.request.views.ViewsPublishRequest;
import com.slack.api.methods.request.views.ViewsUpdateRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersInfoResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.methods.response.views.ViewsPublishResponse;
import com.slack.api.methods.response.views.ViewsUpdateResponse;
import com.slack.api.model.User;
import com.slack.api.model.view.View;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true")
public class SlackClient {

    private final MethodsClient client;
    private final SlackProperties slackProperties;

    public SlackClient(SlackProperties slackProperties) {
        this.slackProperties = slackProperties;
        this.client = Slack.getInstance().methods(slackProperties.botToken());
    }

    public void chatPostMessage(String channelOrUserId, String text) {
        try {
            ChatPostMessageResponse response = client.chatPostMessage(
                    ChatPostMessageRequest.builder()
                            .channel(channelOrUserId)
                            .text(text)
                            .build()
            );

            if (!response.isOk()) {
                log.error("Slack chat.postMessage failed: {}", response.getError());
                throw new com.amalitech.hilfe.slack.exception.SlackClientException(
                        "Failed to send message: " + response.getError());
            }

            log.debug("Message sent to {}", channelOrUserId);
        } catch (IOException | SlackApiException e) {
            log.error("Slack API call failed: chat.postMessage", e);
            throw new com.amalitech.hilfe.slack.exception.SlackClientException("Failed to send message", e);
        }
    }

    public void chatPostMessage(String channelOrUserId, String text, String blocks) {
        try {
            var requestBuilder = ChatPostMessageRequest.builder()
                    .channel(channelOrUserId)
                    .text(text);

            if (blocks != null && !blocks.isBlank()) {
                requestBuilder.blocksAsString(blocks);
            }

            ChatPostMessageResponse response = client.chatPostMessage(requestBuilder.build());

            if (!response.isOk()) {
                log.error("Slack chat.postMessage failed: {}", response.getError());
                throw new com.amalitech.hilfe.slack.exception.SlackClientException(
                        "Failed to send message: " + response.getError());
            }
        } catch (IOException | SlackApiException e) {
            log.error("Slack API call failed: chat.postMessage", e);
            throw new com.amalitech.hilfe.slack.exception.SlackClientException("Failed to send message", e);
        }
    }

    public ViewsOpenResponse viewsOpen(String triggerId, View view) {
        try {
            ViewsOpenResponse response = client.viewsOpen(
                    ViewsOpenRequest.builder()
                            .triggerId(triggerId)
                            .view(view)
                            .build()
            );

            if (!response.isOk()) {
                log.error("Slack views.open failed: {}", response.getError());
                throw new com.amalitech.hilfe.slack.exception.SlackClientException(
                        "Failed to open view: " + response.getError());
            }

            return response;
        } catch (IOException | SlackApiException e) {
            log.error("Slack API call failed: views.open", e);
            throw new com.amalitech.hilfe.slack.exception.SlackClientException("Failed to open view", e);
        }
    }

    public ViewsPublishResponse viewsPublish(String userId, View view) {
        try {
            ViewsPublishResponse response = client.viewsPublish(
                    ViewsPublishRequest.builder()
                            .userId(userId)
                            .view(view)
                            .build()
            );

            if (!response.isOk()) {
                log.error("Slack views.publish failed: {}", response.getError());
                throw new com.amalitech.hilfe.slack.exception.SlackClientException(
                        "Failed to publish view: " + response.getError());
            }

            return response;
        } catch (IOException | SlackApiException e) {
            log.error("Slack API call failed: views.publish", e);
            throw new com.amalitech.hilfe.slack.exception.SlackClientException("Failed to publish view", e);
        }
    }

    public ViewsUpdateResponse viewsUpdate(String viewId, View view) {
        try {
            ViewsUpdateResponse response = client.viewsUpdate(
                    ViewsUpdateRequest.builder()
                            .viewId(viewId)
                            .view(view)
                            .build()
            );

            if (!response.isOk()) {
                log.error("Slack views.update failed: {}", response.getError());
                throw new com.amalitech.hilfe.slack.exception.SlackClientException(
                        "Failed to update view: " + response.getError());
            }

            return response;
        } catch (IOException | SlackApiException e) {
            log.error("Slack API call failed: views.update", e);
            throw new com.amalitech.hilfe.slack.exception.SlackClientException("Failed to update view", e);
        }
    }

    public User usersInfo(String userId) {
        try {
            UsersInfoResponse response = client.usersInfo(req -> req.user(userId));

            if (!response.isOk()) {
                log.error("Slack users.info failed: {}", response.getError());
                throw new com.amalitech.hilfe.slack.exception.SlackClientException(
                        "Failed to get user info: " + response.getError());
            }

            return response.getUser();
        } catch (IOException | SlackApiException e) {
            log.error("Slack API call failed: users.info", e);
            throw new com.amalitech.hilfe.slack.exception.SlackClientException("Failed to get user info", e);
        }
    }

    public boolean isEnabled() {
        return slackProperties.enabled();
    }
}
