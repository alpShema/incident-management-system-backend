package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.ChatbotInteractionResponse;
import com.amalitech.hilfe.dto.ChatbotQueryRequest;
import com.amalitech.hilfe.dto.ChatbotQueryResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.services.ChatbotService;
import com.amalitech.hilfe.services.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.Instant;

@Controller
@RequiredArgsConstructor
public class ChatbotResolver {

    private final ChatbotService chatbotService;

    @MutationMapping
    @PreAuthorize("hasAuthority('chatbot.query')")
    public ChatbotQueryResponse chatbotQuery(
            @Argument ChatbotQueryRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return chatbotService.query(principal.userId(), input.query());
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('chatbot.interactions.read')")
    public PageResponse<ChatbotInteractionResponse> chatbotInteractions(
            @Argument String userId,
            @Argument String outcome,
            @Argument Instant from,
            @Argument Instant to,
            @Argument PageInput page) {
        return chatbotService.listInteractions(userId, outcome, from, to, PageInput.toPageable(page));
    }
}
