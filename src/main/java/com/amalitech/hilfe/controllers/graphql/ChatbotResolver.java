package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.constants.ApiMessages;
import com.amalitech.hilfe.dto.ChatbotAnswerChunk;
import com.amalitech.hilfe.dto.ChatbotInteractionResponse;
import com.amalitech.hilfe.dto.ChatbotQueryRequest;
import com.amalitech.hilfe.dto.ChatbotQueryResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.security.ChatbotRateLimiter;
import com.amalitech.hilfe.services.ChatbotService;
import com.amalitech.hilfe.services.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.time.Instant;

@Controller
@RequiredArgsConstructor
public class ChatbotResolver {

    private final ChatbotService chatbotService;
    private final ChatbotRateLimiter rateLimiter;

    @MutationMapping
    @PreAuthorize("hasAuthority('chatbot.query')")
    public ChatbotQueryResponse chatbotQuery(
            @Argument ChatbotQueryRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Chatbot query processed successfully");
        return chatbotService.query(principal.userId(), input.query());
    }

    @SubscriptionMapping
    @PreAuthorize("hasAuthority('chatbot.query')")
    public Flux<ChatbotAnswerChunk> chatbotAnswerStream(
            @Argument ChatbotQueryRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        if (!rateLimiter.tryAcquire(principal.userId())) {
            throw new ArmsAuthException(ApiMessages.RATE_LIMIT_EXCEEDED, 429);
        }
        return chatbotService.queryStream(principal.userId(), input.query());
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
