package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.MessageResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.PresignedUrlRequest;
import com.amalitech.hilfe.dto.PresignedUrlResponse;
import com.amalitech.hilfe.dto.SendMessageRequest;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageService messageService;

    @QueryMapping
    public PageResponse<MessageResponse> messages(
            @Argument String incidentId,
            @Argument PageInput page,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return PageInput.toPageResponse(
                messageService.listMessages(principal.userId(), principal.roleCode(), incidentId, PageInput.toPageable(page))
        );
    }

    @MutationMapping
    public MessageResponse sendMessage(
            @Argument String incidentId,
            @Argument SendMessageRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return messageService.sendMessage(principal.userId(), principal.roleCode(), incidentId,
                input.content(), input.attachments());
    }

    @MutationMapping
    public boolean deleteMessage(
            @Argument String incidentId,
            @Argument String messageId,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        messageService.deleteMessage(principal.userId(), principal.roleCode(), messageId);
        return true;
    }

    @MutationMapping
    public PresignedUrlResponse generateMessagePresignedUrl(
            @Argument String incidentId,
            @Argument PresignedUrlRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return messageService.generateMessagePresignedUrl(principal.userId(), principal.roleCode(), incidentId, input);
    }
}
