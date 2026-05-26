package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.MessageResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.SendMessageRequest;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Messages", description = "Incident thread messaging")
@RestController
@RequestMapping("/incidents/{incidentId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(
            summary = "Send a message",
            description = "Send a message on an incident thread. Requires access to the incident. "
                    + "After a successful create, the backend broadcasts the same MessageResponse payload to "
                    + "`/topic/incidents/{incidentId}/messages` over WebSocket. "
                    + "See docs/REALTIME_MESSAGING_CONTRACT.md for the full HTTP + WebSocket contract."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Message sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "No access to this incident"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        MessageResponse response = messageService.sendMessage(
                principal.userId(), principal.roleCode(), incidentId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent", response));
    }

    @Operation(
            summary = "List messages",
            description = "Returns paginated message history for an incident, oldest first. "
                    + "Use this endpoint for initial chat load and re-sync after WebSocket reconnect. "
                    + "See docs/REALTIME_MESSAGING_CONTRACT.md for the full HTTP + WebSocket contract."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Messages retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "No access to this incident"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> listMessages(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Messages retrieved",
                PageResponse.from(messageService.listMessages(
                        principal.userId(), principal.roleCode(), incidentId,
                        PageRequest.of(page, size)))));
    }

    @Operation(
            summary = "Delete a message",
            description = "Authors can delete their own messages. Admins can delete any message. "
                    + "See docs/REALTIME_MESSAGING_CONTRACT.md for the full HTTP + WebSocket contract."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Message deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Cannot delete another user's message"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Message not found")
    })
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String incidentId,
            @Parameter(description = "Message ID") @PathVariable String messageId
    ) {
        messageService.deleteMessage(principal.userId(), principal.roleCode(), messageId);
        return ResponseEntity.noContent().build();
    }
}
