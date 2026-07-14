package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.ChatbotInteractionResponse;
import com.amalitech.hilfe.dto.ChatbotQueryRequest;
import com.amalitech.hilfe.dto.ChatbotQueryResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.security.ChatbotRateLimiter;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/chatbot")
@RequiredArgsConstructor
@Tag(name = "Chatbot", description = "FAQ chatbot query and interaction log")
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final ChatbotRateLimiter rateLimiter;

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('" + RbacPermissions.CHATBOT_QUERY + "')")
    @Operation(
            summary = "Ask the chatbot",
            description = "Submit a question and receive a semantically-matched, LLM-composed answer. "
                    + "If no confident match is found, returns an escalation prompt. Rate limited to 30 req/min."
    )
    public ResponseEntity<ApiResponse<ChatbotQueryResponse>> query(
            @Valid @RequestBody ChatbotQueryRequest request,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        if (!rateLimiter.tryAcquire(principal.userId()))
            throw new ArmsAuthException("Rate limit exceeded. Please wait before sending another query.", 429);
        ChatbotQueryResponse response = chatbotService.query(principal.userId(), request.query());
        return ResponseEntity.ok(ApiResponse.success("Query processed", response));
    }

    @GetMapping("/interactions")
    @PreAuthorize("hasAuthority('" + RbacPermissions.CHATBOT_INTERACTIONS_READ + "')")
    @Operation(
            summary = "List chatbot interactions",
            description = "Admin endpoint. Filter by user, outcome, or date range."
    )
    public ResponseEntity<ApiResponse<PageResponse<ChatbotInteractionResponse>>> listInteractions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                "Interactions retrieved",
                chatbotService.listInteractions(userId, outcome, from, to, pageable)
        ));
    }
}
